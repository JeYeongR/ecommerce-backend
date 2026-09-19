package com.ecommerce.backend.order.event.outbox;

import com.ecommerce.backend.order.event.kafka.OrderCompletedMessage;
import com.ecommerce.backend.order.event.kafka.OrderEventProducer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventProducer orderEventProducer;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByIdAsc(
            OutboxStatus.PENDING, Limit.of(BATCH_SIZE));

        for (OutboxEvent event : pending) {
            publish(event);
            event.markPublished(LocalDateTime.now());
        }
    }

    private void publish(OutboxEvent event) {
        OrderCompletedMessage message = new OrderCompletedMessage(
            event.getOrderId(), event.getCustomerId(), event.getTotalPriceAmount());
        try {
            orderEventProducer.publish(message).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[Outbox] 이벤트 발행 실패, 재시도 예정 orderId={}", event.getOrderId(), e);
            throw new IllegalStateException("outbox publish failed for orderId=" + event.getOrderId(), e);
        }
    }
}
