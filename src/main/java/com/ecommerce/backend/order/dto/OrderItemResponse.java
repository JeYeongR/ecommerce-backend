package com.ecommerce.backend.order.dto;

import com.ecommerce.backend.order.domain.OrderItem;

public record OrderItemResponse(
        Long productOptionId,
        String productName,
        Integer orderPrice,
        Integer quantity
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
            orderItem.getProductOption().getId(),
            orderItem.getProductName(),
            orderItem.getOrderPrice().intValue(),
            orderItem.getQuantity()
        );
    }
}
