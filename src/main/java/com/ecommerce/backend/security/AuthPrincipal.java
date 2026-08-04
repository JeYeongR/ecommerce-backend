package com.ecommerce.backend.security;

public record AuthPrincipal(
        Long id,
        AccountType type
) {
}
