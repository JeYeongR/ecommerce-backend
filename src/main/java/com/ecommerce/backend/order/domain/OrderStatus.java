package com.ecommerce.backend.order.domain;

public enum OrderStatus {
    PENDING,         // 접수: 주문 생성 직후, 결제 시작 전
    PAYING,          // 결제중: 결제 시도가 선점되어 결과 대기
    PAID,            // 결제 완료
    PAYMENT_FAILED,  // 결제 실패 (재고는 복구됨, 종료 상태)
    CANCELLED        // 취소
}
