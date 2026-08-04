package com.ecommerce.backend.product.dto;

import com.ecommerce.backend.product.domain.Product;

public record ProductSummaryResponse(
    Long id,
    Long sellerId,
    String name,
    String thumbnailUrl,
    Integer basePrice,
    String status
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
            product.getId(),
            product.getSeller().getId(),
            product.getName(),
            product.getThumbnailUrl(),
            product.getBasePrice().intValue(),
            product.getStatus().name()
        );
    }
}
