package com.ecommerce.backend.order.event;

import com.ecommerce.backend.order.event.kafka.OrderCompletedMessage;
import com.ecommerce.backend.order.event.kafka.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 트랜잭션 커밋 이후에, 트랜잭션과 별개 스레드에서 Kafka로 이벤트를 발행한다.
 * 실제 알림/결제 처리는 NotificationEventConsumer/PaymentEventConsumer가
 * Kafka에서 각자 독립적으로(별도 컨슈머 그룹) 수행한다.
 *
 * 한계: DB 커밋과 Kafka 발행 사이 원자성이 없다 — 커밋 후 발행 직전에 프로세스가
 * 죽거나 발행이 실패하면 이벤트가 유실된다. 다음 단계(Outbox 패턴)에서 해소 예정.
 */
@Component
@RequiredArgsConstructor
public class OrderCompletedEventListener {

    private final OrderEventProducer orderEventProducer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishToKafka(OrderCompletedEvent event) {
        orderEventProducer.publish(new OrderCompletedMessage(
            event.orderId(), event.customerId(), event.totalPrice().intValue()));
    }
}
