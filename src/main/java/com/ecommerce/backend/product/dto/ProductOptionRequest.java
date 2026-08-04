package com.ecommerce.backend.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductOptionRequest(
    @NotBlank String optionName,
    @NotNull @PositiveOrZero Integer additionalPrice,
    @NotNull @PositiveOrZero Integer stock
) {
}
