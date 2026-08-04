package com.ecommerce.backend.order.dto;

import com.ecommerce.backend.order.domain.Order;
import java.util.List;

public record OrderResponse(
    Long id,
    Long customerId,
    String status,
    Integer totalPrice,
    List<OrderItemResponse> items
) {

    public static OrderResponse of(Order order, List<OrderItemResponse> items) {
        return new OrderResponse(
            order.getId(),
            order.getCustomer().getId(),
            order.getStatus().name(),
            order.getTotalPrice().intValue(),
            items
        );
    }
}
