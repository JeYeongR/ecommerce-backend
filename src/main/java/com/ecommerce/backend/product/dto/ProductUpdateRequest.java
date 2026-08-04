package com.ecommerce.backend.product.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ProductUpdateRequest(
    String name,
    @PositiveOrZero Integer basePrice,
    String description
) {
}
