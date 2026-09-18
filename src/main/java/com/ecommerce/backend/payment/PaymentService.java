package com.ecommerce.backend.payment;

import com.ecommerce.backend.order.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {

    private static final long SIMULATED_LATENCY_MS = 150;

    public void capturePayment(Order order) {
        sleep(SIMULATED_LATENCY_MS);
        log.info("[Payment] 결제 내역 기록 orderId={} totalPrice={}",
            order.getId(), order.getTotalPrice());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
