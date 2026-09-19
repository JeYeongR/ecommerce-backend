package com.ecommerce.backend.order.event.kafka;

import com.ecommerce.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = OrderEventProducer.ORDER_COMPLETED_TOPIC, groupId = "notification-service")
    public void onOrderCompleted(OrderCompletedMessage message) {
        notificationService.notifyOrderCompleted(message.orderId(), message.customerId());
    }
}
