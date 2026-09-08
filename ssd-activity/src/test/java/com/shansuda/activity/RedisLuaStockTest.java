package com.shansuda.activity;

import com.shansuda.activity.service.RedisLuaAtomicStock;
import com.shansuda.activity.service.StockResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.Socket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV_TASK §10：Lua 并发秒杀正确性。优先 Testcontainers Redis，否则本机 6389。
 * 不把 P95/QPS 写进断言。
 */
class RedisLuaStockTest {

    private static GenericContainer<?> container;
    private static LettuceConnectionFactory factory;
    private static RedisLuaAtomicStock stock;

    @BeforeAll
    static void startRedis() {
        String host = null;
        int port = -1;
        try {
            container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofSeconds(45));
            container.start();
            host = container.getHost();
            port = container.getMappedPort(6379);
        } catch (Throwable ex) {
            if (reachable("127.0.0.1", 6389)) {
                host = "127.0.0.1";
                port = 6389;
            } else {
                Assumptions.abort("需要 Testcontainers Redis 或本机 6389: " + ex.getMessage());
            }
        }
        factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        stock = new RedisLuaAtomicStock(template);
    }

    @AfterAll
    static void stop() {
        if (factory != null) {
            factory.destroy();
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void concurrentSeckillDoesNotGoNegative() throws Exception {
        String sku = "it:stock:" + UUID.randomUUID();
        int inventory = 20;
        stock.initStock(sku, inventory);
        int threads = 80;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            int user = i;
            pool.submit(() -> {
                try {
                    start.await();
                    StockResult r = stock.deduct(sku, "it:idem:" + sku + ":" + user, "ok-" + user, 120);
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
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();
        int remain = stock.remaining(sku);
        assertTrue(remain >= 0);
        assertEquals(inventory, success.get());
        assertEquals(0, remain);
        assertEquals(threads - inventory, empty.get());
    }

    @Test
    void replayDoesNotDeductTwice() {
        String sku = "it:stock:" + UUID.randomUUID();
        stock.initStock(sku, 5);
        String idem = "it:idem:" + sku + ":u1";
        StockResult first = stock.deduct(sku, idem, "payload-1", 120);
        StockResult second = stock.deduct(sku, idem, "payload-other", 120);
        assertEquals(1, first.code());
        assertEquals(-1, second.code());
        assertEquals("payload-1", second.payload());
        assertEquals(4, stock.remaining(sku));
    }

    @Test
    void sameUserConcurrentOnlyOneSuccess() throws Exception {
        String sku = "it:stock:" + UUID.randomUUID();
        stock.initStock(sku, 8);
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger replay = new AtomicInteger();
        String idem = "it:idem:" + sku + ":same-user";
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    StockResult r = stock.deduct(sku, idem, "payload-1", 120);
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
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, success.get());
        assertEquals(threads - 1, replay.get());
        assertEquals(7, stock.remaining(sku));
    }

    @Test
    void stockOneTwoUsersOnlyOneWins() throws Exception {
        String sku = "it:stock:" + UUID.randomUUID();
        stock.initStock(sku, 1);
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
                    StockResult r = stock.deduct(sku, "it:idem:" + sku + ":u" + u, "ok-" + u, 120);
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
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, success.get());
        assertEquals(1, empty.get());
        assertEquals(0, stock.remaining(sku));
    }

    private static boolean reachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
