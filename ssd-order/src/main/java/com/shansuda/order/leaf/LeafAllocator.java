package com.shansuda.order.leaf;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** 美团 Leaf 号段模式精简实现：双 buffer，不依赖时钟。 */
public class LeafAllocator {

    private final Supplier<Segment> fetcher;
    private final Object lock = new Object();
    private final ExecutorService preload = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "leaf-preload");
        t.setDaemon(true);
        return t;
    });
    private Segment current;
    private Segment next;
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public LeafAllocator(JdbcTemplate jdbc, String bizTag) {
        this(() -> {
            jdbc.update("UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag = ?", bizTag);
            return jdbc.queryForObject("SELECT max_id, step FROM leaf_alloc WHERE biz_tag = ?", (rs, i) -> {
                long maxId = rs.getLong("max_id");
                int step = rs.getInt("step");
                return new Segment(maxId - step + 1, maxId);
            }, bizTag);
        });
    }

    public LeafAllocator(Supplier<Segment> fetcher) {
        this.fetcher = fetcher;
        this.current = fetchSegment();
    }

    public long nextId() {
        synchronized (lock) {
            if (current.hasRemaining()) {
                long id = current.next();
                prefetchIfNeeded();
                return id;
            }
            switchBuffer();
            return current.next();
        }
    }

    private void prefetchIfNeeded() {
        if (!current.low() || next != null || !loading.compareAndSet(false, true)) {
            return;
        }
        preload.execute(() -> {
            try {
                Segment loaded = fetchSegment();
                synchronized (lock) {
                    next = loaded;
                    lock.notifyAll();
                }
            } finally {
                loading.set(false);
            }
        });
    }

    private void switchBuffer() {
        if (next == null) {
            if (loading.compareAndSet(false, true)) {
                try {
                    next = fetchSegment();
                } finally {
                    loading.set(false);
                    lock.notifyAll();
                }
            } else {
                long deadline = System.currentTimeMillis() + 2000;
                while (next == null && System.currentTimeMillis() < deadline) {
                    try {
                        lock.wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (next != null) {
            current = next;
            next = null;
        } else {
            current = fetchSegment();
        }
    }

    private Segment fetchSegment() {
        return fetcher.get();
    }

    public static Segment segment(long start, long max) {
        return new Segment(start, max);
    }

    public static final class Segment {
        private long current;
        private final long max;
        private final long start;

        private Segment(long start, long max) {
            this.start = start;
            this.current = start;
            this.max = max;
        }

        private boolean hasRemaining() {
            return current <= max;
        }

        private long next() {
            return current++;
        }

        private boolean low() {
            long total = max - start + 1;
            long left = max - current + 1;
            return left * 2 < total;
        }
    }
}
