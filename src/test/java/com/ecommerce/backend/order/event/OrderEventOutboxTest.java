package com.ecommerce.backend.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.order.domain.Order;
import com.ecommerce.backend.order.domain.OrderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class OrderEventOutboxTest {

    private final List<OutboxEvent> store = new ArrayList<>();
    private final Set<Long> failingOrderIds = new java.util.HashSet<>();
    private final List<OrderCompletedMessage> sent = new ArrayList<>();
    private OrderEventOutbox outbox;

    @BeforeEach
    void setUp() {
        OutboxEventRepository repository = new OutboxEventRepository() {
            @Override
            public OutboxEvent save(OutboxEvent event) {
                store.add(event);
                return event;
            }

            @Override
            public List<OutboxEvent> lockPending(int limit) {
                return store.stream().filter(e -> e.getStatus() == OutboxStatus.PENDING).limit(limit).toList();
            }
        };
        OrderEventSender sender = message -> {
            if (failingOrderIds.contains(message.orderId())) {
                throw new IllegalStateException("kafka down");
            }
            sent.add(message);
        };
        outbox = new OrderEventOutbox(repository, sender);
    }

    private Order order(long id, long customerId, int price) {
        return Order.builder()
            .id(id)
            .customer(Customer.builder().id(customerId).build())
            .status(OrderStatus.PENDING)
            .totalPrice(Money.of(price))
            .build();
    }

    @Test
    void record는_주문정보를_PENDING_이벤트로_기록한다() {
        outbox.record(order(100L, 1L, 2000));

        assertThat(store).singleElement().satisfies(e -> {
            assertThat(e.getOrderId()).isEqualTo(100L);
            assertThat(e.getCustomerId()).isEqualTo(1L);
            assertThat(e.getTotalPriceAmount()).isEqualTo(2000);
            assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
        });
    }

    @Test
    void record는_트랜잭션이_없으면_실패하도록_MANDATORY다() throws Exception {
        Transactional tx = OrderEventOutbox.class.getMethod("record", Order.class).getAnnotation(Transactional.class);

        assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void relay는_PENDING_이벤트를_발행하고_PUBLISHED로_표시한다() {
        outbox.record(order(1L, 10L, 1000));

        outbox.relay();

        assertThat(sent).containsExactly(new OrderCompletedMessage(1L, 10L, 1000));
        assertThat(store.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(store.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    void relay는_이미_발행된_이벤트를_다시_보내지_않는다() {
        outbox.record(order(1L, 10L, 1000));
        outbox.relay();

        outbox.relay();

        assertThat(sent).hasSize(1);
    }

    @Test
    void relay는_한_건이_실패해도_나머지를_발행하고_실패건은_PENDING으로_남긴다() {
        outbox.record(order(1L, 10L, 1000));
        outbox.record(order(2L, 10L, 2000));
        outbox.record(order(3L, 10L, 3000));
        failingOrderIds.add(1L);

        outbox.relay();

        assertThat(sent).extracting(OrderCompletedMessage::orderId).containsExactly(2L, 3L);
        assertThat(store).extracting(OutboxEvent::getStatus)
            .containsExactly(OutboxStatus.PENDING, OutboxStatus.PUBLISHED, OutboxStatus.PUBLISHED);
    }

    @Test
    void 실패건은_다음_relay에서_재시도된다() {
        outbox.record(order(1L, 10L, 1000));
        failingOrderIds.add(1L);
        outbox.relay();

        failingOrderIds.clear();
        outbox.relay();

        assertThat(sent).extracting(OrderCompletedMessage::orderId).containsExactly(1L);
        assertThat(store.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }
}
