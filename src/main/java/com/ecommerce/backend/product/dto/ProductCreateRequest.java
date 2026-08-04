package com.ecommerce.backend.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record ProductCreateRequest(
    @NotBlank String name,
    @NotNull @PositiveOrZero Integer basePrice,
    String description,
    @NotBlank String thumbnailUrl,
    List<String> images,
    @NotEmpty List<@Valid ProductOptionRequest> options
) {
}
