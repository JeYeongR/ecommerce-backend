package com.ecommerce.backend.order.event;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KafkaOrderEventSender implements OrderEventSender {

    static final String ORDER_COMPLETED_TOPIC = "order.completed";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void send(OrderCompletedMessage message) {
        try {
            kafkaTemplate.send(ORDER_COMPLETED_TOPIC, message.orderId().toString(), message)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("send interrupted orderId=" + message.orderId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("send failed orderId=" + message.orderId(), e);
        }
    }
}
