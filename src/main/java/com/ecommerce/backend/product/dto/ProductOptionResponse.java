package com.ecommerce.backend.product.dto;

import com.ecommerce.backend.product.domain.ProductOption;

public record ProductOptionResponse(
        Long id,
        String optionName,
        Integer additionalPrice,
        Integer stock
) {
    public static ProductOptionResponse from(ProductOption option) {
        return new ProductOptionResponse(
            option.getId(),
            option.getOptionName(),
            option.getAdditionalPrice().intValue(),
            option.getStock()
        );
    }
}
