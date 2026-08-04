package com.ecommerce.backend.auth.controller;

import com.ecommerce.backend.auth.dto.LoginRequest;
import com.ecommerce.backend.auth.dto.SellerSignupRequest;
import com.ecommerce.backend.auth.dto.SellerSignupResponse;
import com.ecommerce.backend.auth.dto.TokenResponse;
import com.ecommerce.backend.auth.service.SellerAuthService;
import com.ecommerce.backend.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sellers")
public class SellerAuthController {

    private final SellerAuthService sellerAuthService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SellerSignupResponse>> signup(@Valid @RequestBody SellerSignupRequest request) {
        SellerSignupResponse response = sellerAuthService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = sellerAuthService.login(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
