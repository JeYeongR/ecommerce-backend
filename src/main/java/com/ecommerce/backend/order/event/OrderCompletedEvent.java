package com.ecommerce.backend.order.event;

import com.ecommerce.backend.common.domain.Money;

public record OrderCompletedEvent(Long orderId, Long customerId, Money totalPrice) {
}
