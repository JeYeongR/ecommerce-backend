package com.ecommerce.backend.product.dto;

import com.ecommerce.backend.product.domain.Product;
import java.util.List;

public record ProductResponse(
    Long id,
    Long sellerId,
    String name,
    String description,
    String thumbnailUrl,
    Integer basePrice,
    Integer salesCount,
    List<String> images,
    List<ProductOptionResponse> options
) {
    public static ProductResponse of(
        Product product,
        List<String> images,
        List<ProductOptionResponse> options
    ) {
        return new ProductResponse(
            product.getId(),
            product.getSeller().getId(),
            product.getName(),
            product.getDescription(),
            product.getThumbnailUrl(),
            product.getBasePrice().intValue(),
            product.getSalesCount(),
            images,
            options
        );
    }
}
