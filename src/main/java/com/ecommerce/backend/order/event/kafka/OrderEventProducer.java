package com.ecommerce.backend.order.event.kafka;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    public static final String ORDER_COMPLETED_TOPIC = "order.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<SendResult<String, Object>> publish(OrderCompletedMessage message) {
        return kafkaTemplate.send(ORDER_COMPLETED_TOPIC, message.orderId().toString(), message);
    }
}
