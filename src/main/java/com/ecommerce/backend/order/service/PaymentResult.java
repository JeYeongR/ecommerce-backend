package com.ecommerce.backend.order.service;

public enum PaymentResult {
    PAID,
    /** PG가 명확히 거절. 호출한 쪽이 결제 실패를 확정한다. */
    DECLINED,
    /** PG 결과를 알 수 없음. 주문은 결제중으로 남는다. */
    UNKNOWN,
    /** 이미 다른 요청이 선점했거나 처리됨. */
    SKIPPED
}
