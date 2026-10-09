package com.ecommerce.backend.order.service;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.order.domain.Order;
import com.ecommerce.backend.order.event.OrderEventOutbox;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.payment.PaymentDeclinedException;
import com.ecommerce.backend.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 결제. 같은 주문에 요청이 여러 번, 여러 인스턴스에서 동시에 와도 결제는 한 번만 시도한다.
 * 선점(PENDING -> PAYING)은 짧은 트랜잭션으로 먼저 커밋하고, PG 호출 동안에는 DB 트랜잭션을 잡지 않는다.
 * PAID 확정과 주문 완료 이벤트 기록은 같은 트랜잭션이라, 결제가 끝난 주문에 대해서만 이벤트가 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;
    private final OrderEventOutbox orderEventOutbox;

    public PaymentResult pay(Long orderId, Money amount) {
        boolean claimed = transactionTemplate.execute(status -> orderRepository.claimForPayment(orderId) == 1);
        if (!claimed) {
            log.info("[Payment] 이미 처리 중이거나 처리된 주문, 건너뜀 orderId={}", orderId);
            return PaymentResult.SKIPPED;
        }

        try {
            paymentService.capturePayment(orderId, amount);
        } catch (PaymentDeclinedException e) {
            log.warn("[Payment] PG 결제 거절 orderId={}", orderId, e);
            return PaymentResult.DECLINED;
        } catch (RuntimeException e) {
            // 결과를 알 수 없으므로 PAYING으로 남긴다. 결제중 방치 건 배치가 PG 조회로 확정한다.
            log.error("[Payment] PG 호출 결과 불명, 결제중 유지 orderId={}", orderId, e);
            return PaymentResult.UNKNOWN;
        }

        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            order.markPaid();
            orderEventOutbox.record(order);
        });
        return PaymentResult.PAID;
    }
}
