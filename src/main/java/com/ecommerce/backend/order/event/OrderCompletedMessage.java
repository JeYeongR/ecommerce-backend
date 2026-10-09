package com.ecommerce.backend.order.event;

public record OrderCompletedMessage(Long orderId, Long customerId, int totalPriceAmount) {
}
