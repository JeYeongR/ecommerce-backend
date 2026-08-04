package com.ecommerce.backend.order.controller;

import com.ecommerce.backend.common.ApiResponse;
import com.ecommerce.backend.common.PageRequest;
import com.ecommerce.backend.common.PageResponse;
import com.ecommerce.backend.order.dto.OrderCreateRequest;
import com.ecommerce.backend.order.dto.OrderResponse;
import com.ecommerce.backend.order.dto.OrderSummaryResponse;
import com.ecommerce.backend.order.service.OrderService;
import com.ecommerce.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> create(
        @AuthenticationPrincipal AuthPrincipal principal,
        @Valid @RequestBody OrderCreateRequest request
    ) {
        OrderResponse response = orderService.create(principal.id(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
        @AuthenticationPrincipal AuthPrincipal principal,
        PageRequest pageRequest
    ) {
        PageResponse<OrderSummaryResponse> response = orderService.list(principal.id(), pageRequest.toPageable());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> get(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long orderId
    ) {
        OrderResponse response = orderService.get(principal.id(), orderId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long orderId
    ) {
        OrderResponse response = orderService.cancel(principal.id(), orderId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
