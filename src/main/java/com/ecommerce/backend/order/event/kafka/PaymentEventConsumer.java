package com.ecommerce.backend.order.event.kafka;

import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = OrderEventProducer.ORDER_COMPLETED_TOPIC, groupId = "payment-service")
    public void onOrderCompleted(OrderCompletedMessage message) {
        paymentService.capturePayment(message.orderId(), Money.of(message.totalPriceAmount()));
    }
}
