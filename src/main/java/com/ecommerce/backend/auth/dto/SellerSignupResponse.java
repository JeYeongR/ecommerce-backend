package com.ecommerce.backend.auth.dto;

public record SellerSignupResponse(
        Long id,
        String email,
        String shopName
) {
}
