package com.ecommerce.backend.order.event;

import com.ecommerce.backend.notification.NotificationService;
import com.ecommerce.backend.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 트랜잭션 커밋 이후에, 트랜잭션과 별개 스레드에서 후처리한다.
 * 인메모리 이벤트라 프로세스가 죽으면 유실되고 다른 인스턴스로 전달 안 됨 — Chain3 다음 단계(Kafka)에서 해소.
 */
@Component
@RequiredArgsConstructor
public class OrderCompletedEventListener {

    private final NotificationService notificationService;
    private final PaymentService paymentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notify(OrderCompletedEvent event) {
        notificationService.notifyOrderCompleted(event.orderId(), event.customerId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void settle(OrderCompletedEvent event) {
        paymentService.capturePayment(event.orderId(), event.totalPrice());
    }
}
