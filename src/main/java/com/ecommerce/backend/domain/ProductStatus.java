package com.ecommerce.backend.domain;

public enum ProductStatus {
    ON_SALE,     // 기본 판매 상태
    SOLD_OUT     // 재고 소진 (stock 기반 파생값의 역정규화)
}
