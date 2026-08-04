package com.ecommerce.backend.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 고객입니다."),
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 판매자입니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인 주문만 처리할 수 있습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "이미 취소된 주문입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인 상품만 처리할 수 있습니다."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품 옵션입니다."),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
