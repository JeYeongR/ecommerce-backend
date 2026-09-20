package com.ecommerce.backend.payment;

import com.ecommerce.backend.common.domain.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {

    private static final long SIMULATED_LATENCY_MS = 150;

    public void capturePayment(Long orderId, Money totalPrice) {
        sleep(SIMULATED_LATENCY_MS);
        log.info("[Payment] PG 결제 승인 API 호출 orderId={} amount={}", orderId, totalPrice.intValue());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
