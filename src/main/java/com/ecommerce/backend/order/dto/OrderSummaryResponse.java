package com.ecommerce.backend.order.dto;

import com.ecommerce.backend.order.domain.Order;

public record OrderSummaryResponse(
    Long id,
    String status,
    Integer totalPrice
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
            order.getId(),
            order.getStatus().name(),
            order.getTotalPrice().intValue()
        );
    }
}
