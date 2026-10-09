-- 주문 상태에 결제 단계 추가 (PAYING, PAID, PAYMENT_FAILED)
-- ddl-auto=validate 이므로 배포 전에 수동으로 적용한다. 기존 데이터(PENDING, CANCELLED)에는 영향 없음.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING', 'PAYING', 'PAID', 'PAYMENT_FAILED', 'CANCELLED'));
