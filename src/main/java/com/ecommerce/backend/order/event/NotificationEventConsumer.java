package com.ecommerce.backend.order.event;

import com.ecommerce.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaOrderEventSender.ORDER_COMPLETED_TOPIC, groupId = "notification-service")
    public void onOrderCompleted(OrderCompletedMessage message) {
        notificationService.notifyOrderCompleted(message.orderId(), message.customerId());
    }
}
