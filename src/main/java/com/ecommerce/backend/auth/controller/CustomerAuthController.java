package com.ecommerce.backend.auth.controller;

import com.ecommerce.backend.auth.dto.CustomerSignupResponse;
import com.ecommerce.backend.auth.dto.LoginRequest;
import com.ecommerce.backend.auth.dto.SignupRequest;
import com.ecommerce.backend.auth.dto.TokenResponse;
import com.ecommerce.backend.auth.service.CustomerAuthService;
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
@RequestMapping("/api/customers")
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<CustomerSignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        CustomerSignupResponse response = customerAuthService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = customerAuthService.login(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
