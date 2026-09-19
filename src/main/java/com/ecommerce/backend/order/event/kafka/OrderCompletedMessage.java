package com.ecommerce.backend.order.event.kafka;

public record OrderCompletedMessage(Long orderId, Long customerId, int totalPriceAmount) {
}
