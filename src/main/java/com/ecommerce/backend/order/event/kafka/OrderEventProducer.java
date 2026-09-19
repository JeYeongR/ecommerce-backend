package com.ecommerce.backend.order.event.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    public static final String ORDER_COMPLETED_TOPIC = "order.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(OrderCompletedMessage message) {
        kafkaTemplate.send(ORDER_COMPLETED_TOPIC, message.orderId().toString(), message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka] 주문완료 이벤트 발행 실패 orderId={}", message.orderId(), ex);
                }
            });
    }
}
