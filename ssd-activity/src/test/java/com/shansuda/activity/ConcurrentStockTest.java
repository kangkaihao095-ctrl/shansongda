package com.shansuda.activity;

import com.shansuda.activity.service.InMemoryAtomicStock;
import com.shansuda.activity.service.StockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentStockTest {

    @Test
    void seckillDoesNotOversell() throws Exception {
        InMemoryAtomicStock stock = new InMemoryAtomicStock();
        stock.initStock("sku", 20);
        int threads = 80;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            int user = i;
            pool.submit(() -> {
                try {
                    start.await();
                    StockResult r = stock.deduct("sku", "u" + user, "ok-" + user, 60);
                    if (r.success()) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(20, success.get());
        assertEquals(0, stock.remaining("sku"));
    }

    @Test
    void replayDoesNotDeductTwice() {
        InMemoryAtomicStock stock = new InMemoryAtomicStock();
        stock.initStock("sku", 5);
        StockResult first = stock.deduct("sku", "u1", "payload-1", 60);
        StockResult second = stock.deduct("sku", "u1", "payload-other", 60);
        assertEquals(1, first.code());
        assertEquals(-1, second.code());
        assertEquals("payload-1", second.payload());
        assertEquals(4, stock.remaining("sku"));
    }

    @Test
    void uniqueWinners() throws Exception {
        InMemoryAtomicStock stock = new InMemoryAtomicStock();
        stock.initStock("sku", 3);
        Set<String> winners = ConcurrentHashMap.newKeySet();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            int user = i;
            threads.add(new Thread(() -> {
                StockResult r = stock.deduct("sku", "u" + user, "p" + user, 60);
                if (r.success()) {
                    winners.add("u" + user);
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(3, winners.size());
    }

    @Test
    void sameUserConcurrentOnlyOneSuccess() throws Exception {
        InMemoryAtomicStock stock = new InMemoryAtomicStock();
        stock.initStock("sku", 8);
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger replay = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    StockResult r = stock.deduct("sku", "user-1:SECKILL:1001", "payload-1", 60);
                    if (r.success()) {
                        success.incrementAndGet();
                    } else if (r.replay()) {
                        replay.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, success.get());
        assertEquals(threads - 1, replay.get());
        assertEquals(7, stock.remaining("sku"));
    }

    @Test
    void stockOneTwoUsersOnlyOneWins() throws Exception {
        InMemoryAtomicStock stock = new InMemoryAtomicStock();
        stock.initStock("sku", 1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        for (int user = 0; user < 2; user++) {
            int u = user;
            pool.submit(() -> {
                try {
                    start.await();
                    StockResult r = stock.deduct("sku", "u" + u, "p" + u, 60);
                    if (r.success()) {
                        success.incrementAndGet();
                    } else if (r.empty()) {
                        empty.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, success.get());
        assertEquals(1, empty.get());
        assertEquals(0, stock.remaining("sku"));
    }
}
