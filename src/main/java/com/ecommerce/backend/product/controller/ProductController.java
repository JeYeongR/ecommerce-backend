package com.ecommerce.backend.product.controller;

import com.ecommerce.backend.common.ApiResponse;
import com.ecommerce.backend.common.PageRequest;
import com.ecommerce.backend.common.PageResponse;
import com.ecommerce.backend.product.dto.ProductCreateRequest;
import com.ecommerce.backend.product.dto.ProductResponse;
import com.ecommerce.backend.product.dto.ProductSummaryResponse;
import com.ecommerce.backend.product.dto.ProductUpdateRequest;
import com.ecommerce.backend.product.service.ProductService;
import com.ecommerce.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
        @AuthenticationPrincipal AuthPrincipal principal,
        @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.create(principal.id(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> list(PageRequest pageRequest) {
        PageResponse<ProductSummaryResponse> response = productService.list(pageRequest.toPageable());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable Long productId) {
        ProductResponse response = productService.get(productId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductResponse response = productService.update(principal.id(), productId, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable Long productId
    ) {
        productService.delete(principal.id(), productId);

        return ResponseEntity.noContent().build();
    }
}
