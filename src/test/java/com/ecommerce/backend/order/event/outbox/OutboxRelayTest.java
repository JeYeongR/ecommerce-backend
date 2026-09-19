package com.ecommerce.backend.order.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.ecommerce.backend.order.event.kafka.OrderCompletedMessage;
import com.ecommerce.backend.order.event.kafka.OrderEventProducer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, orderEventProducer);
    }

    private OutboxEvent pendingEvent(Long orderId) {
        return OutboxEvent.builder()
            .orderId(orderId)
            .customerId(1L)
            .totalPriceAmount(10000)
            .build();
    }

    @Test
    void 대기중인_이벤트를_발행하고_발행완료로_표시한다() {
        OutboxEvent event = pendingEvent(1L);
        given(outboxEventRepository.findByStatusOrderByIdAsc(any(OutboxStatus.class), any(Limit.class)))
            .willReturn(List.of(event));
        given(orderEventProducer.publish(any(OrderCompletedMessage.class)))
            .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxRelay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(orderEventProducer).publish(new OrderCompletedMessage(1L, 1L, 10000));
    }

    @Test
    void 대기중인_이벤트가_없으면_아무것도_발행하지_않는다() {
        given(outboxEventRepository.findByStatusOrderByIdAsc(any(OutboxStatus.class), any(Limit.class)))
            .willReturn(List.of());

        outboxRelay.relay();

        verifyNoMoreInteractions(orderEventProducer);
    }

    @Test
    void 발행이_실패하면_예외를_던지고_발행완료로_표시하지_않는다() {
        OutboxEvent event = pendingEvent(2L);
        given(outboxEventRepository.findByStatusOrderByIdAsc(any(OutboxStatus.class), any(Limit.class)))
            .willReturn(List.of(event));
        given(orderEventProducer.publish(any(OrderCompletedMessage.class)))
            .willReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        assertThatThrownBy(() -> outboxRelay.relay())
            .isInstanceOf(IllegalStateException.class);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void 배치_중_하나가_실패하면_그_뒤_이벤트는_이번_폴링에서_발행되지_않는다() {
        OutboxEvent first = pendingEvent(1L);
        OutboxEvent second = pendingEvent(2L);
        given(outboxEventRepository.findByStatusOrderByIdAsc(any(OutboxStatus.class), any(Limit.class)))
            .willReturn(List.of(first, second));
        given(orderEventProducer.publish(any(OrderCompletedMessage.class)))
            .willReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        assertThatThrownBy(() -> outboxRelay.relay())
            .isInstanceOf(IllegalStateException.class);

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(orderEventProducer).publish(new OrderCompletedMessage(1L, 1L, 10000));
        verifyNoMoreInteractions(orderEventProducer);
    }
}
