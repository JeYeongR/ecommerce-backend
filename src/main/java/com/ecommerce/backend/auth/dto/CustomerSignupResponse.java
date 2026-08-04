package com.ecommerce.backend.auth.dto;

public record CustomerSignupResponse(
        Long id,
        String email,
        String nickname
) {
}
