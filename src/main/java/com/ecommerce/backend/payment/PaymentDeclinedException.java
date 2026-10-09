package com.ecommerce.backend.payment;

/** PG가 결제를 명확히 거절했다. 타임아웃처럼 결과를 알 수 없는 실패와 구분한다. */
public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
