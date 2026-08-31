package com.ecommerce.backend.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RedisLockManagerTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private RedisLockManager redisLockManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 같은_키로_동시_접근하면_상호배제된다() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    redisLockManager.withLock("same-key", () -> {
                        int current = counter.get();
                        counter.set(current + 1);
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(counter.get()).isEqualTo(THREAD_COUNT);
    }

    @Test
    void 락이_풀리면_레디스에서_키가_삭제된다() {
        redisLockManager.withLock("cleanup-key", () -> null);

        assertThat(redisTemplate.hasKey("lock:cleanup-key")).isFalse();
    }
}
