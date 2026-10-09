package com.ecommerce.backend.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** relay를 여러 인스턴스가 동시에 돌리는 상황을 같은 DB에 relay 여러 개를 동시에 호출해 흉내낸다. */
@SpringBootTest
class OrderEventOutboxConcurrencyTest {

    private static final long BASE_ORDER_ID = 9_000_000_000L;
    private static final int EVENT_COUNT = 50;
    private static final int RELAY_COUNT = 5;

    static final Map<Long, AtomicInteger> SENT = new ConcurrentHashMap<>();

    @TestConfiguration
    static class SlowCountingSenderConfig {
        @Bean
        @Primary
        OrderEventSender countingSender() {
            return message -> {
                sleep(20);
                SENT.computeIfAbsent(message.orderId(), id -> new AtomicInteger()).incrementAndGet();
            };
        }

        private static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Autowired
    private OrderEventOutbox outbox;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from outbox_event where order_id >= ?", BASE_ORDER_ID);
        SENT.clear();
    }

    @Test
    void relay_여러_개가_동시에_돌아도_이벤트는_한_번씩만_발행된다() throws Exception {
        transactionTemplate.executeWithoutResult(status -> LongStream.range(0, EVENT_COUNT).forEach(i ->
            repository.save(OutboxEvent.builder()
                .orderId(BASE_ORDER_ID + i).customerId(1L).totalPriceAmount(1000).build())));

        ExecutorService executor = Executors.newFixedThreadPool(RELAY_COUNT);
        CountDownLatch ready = new CountDownLatch(RELAY_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = IntStream.range(0, RELAY_COUNT).mapToObj(i -> executor.submit(() -> {
            ready.countDown();
            start.await();
            outbox.relay();
            return null;
        })).collect(java.util.stream.Collectors.toList());

        ready.await();
        start.countDown();
        for (var future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();

        List<Long> orderIds = LongStream.range(0, EVENT_COUNT).map(i -> BASE_ORDER_ID + i).boxed().toList();
        assertThat(orderIds).allSatisfy(id -> assertThat(SENT.get(id)).as("orderId=%d", id).isNotNull());
        assertThat(orderIds).allSatisfy(id -> assertThat(SENT.get(id).get()).as("orderId=%d", id).isEqualTo(1));
    }
}
