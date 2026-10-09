package com.ecommerce.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.order.domain.Order;
import com.ecommerce.backend.order.domain.OrderStatus;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.payment.PaymentService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 같은 주문의 결제 요청이 여러 인스턴스에서 동시에 와도 결제는 한 번만 시도되고, 완료 이벤트도 한 번만 기록된다. */
@SpringBootTest
class OrderPaymentConcurrencyTest {

    private static final int DELIVERIES = 5;
    static final AtomicInteger CAPTURE_COUNT = new AtomicInteger();

    @TestConfiguration
    static class CountingPaymentConfig {
        @Bean
        @Primary
        PaymentService countingPaymentService() {
            return new PaymentService() {
                @Override
                public void capturePayment(Long orderId, Money totalPrice) {
                    CAPTURE_COUNT.incrementAndGet();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
        }
    }

    @Autowired
    private OrderPaymentService orderPaymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customerId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        CAPTURE_COUNT.set(0);
        Customer customer = customerRepository.save(Customer.builder()
            .email("payment-" + UUID.randomUUID() + "@test.com")
            .password("password")
            .name("결제테스트")
            .nickname("payment")
            .build());
        customerId = customer.getId();
        orderId = orderRepository.save(Order.builder()
            .customer(customer)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.of(1000))
            .build()).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from outbox_event where order_id = ?", orderId);
        orderRepository.deleteById(orderId);
        customerRepository.deleteById(customerId);
    }

    @Test
    void 같은_주문의_결제가_동시에_여러_번_요청돼도_결제와_이벤트는_한_번만_되고_PAID가_된다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(DELIVERIES);
        CountDownLatch ready = new CountDownLatch(DELIVERIES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = IntStream.range(0, DELIVERIES).mapToObj(i -> executor.submit(() -> {
            ready.countDown();
            start.await();
            orderPaymentService.pay(orderId, Money.of(1000));
            return null;
        })).collect(java.util.stream.Collectors.toList());

        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(CAPTURE_COUNT.get()).isEqualTo(1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(outboxCount()).isEqualTo(1);
    }

    @Test
    void 이미_PAID인_주문에_결제가_다시_요청돼도_결제하지_않는다() {
        orderPaymentService.pay(orderId, Money.of(1000));

        orderPaymentService.pay(orderId, Money.of(1000));

        assertThat(CAPTURE_COUNT.get()).isEqualTo(1);
        assertThat(outboxCount()).isEqualTo(1);
    }

    private Integer outboxCount() {
        return jdbcTemplate.queryForObject("select count(*) from outbox_event where order_id = ?", Integer.class, orderId);
    }
}
