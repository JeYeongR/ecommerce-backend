package com.ecommerce.backend.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
    @NotNull Long productOptionId,
    @NotNull @Positive Integer quantity
) {
}
