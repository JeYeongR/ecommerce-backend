package com.ecommerce.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SellerSignupRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotBlank String shopName
) {
}
