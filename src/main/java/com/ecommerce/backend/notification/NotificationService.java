package com.ecommerce.backend.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    private static final long SIMULATED_LATENCY_MS = 150;

    public void notifyOrderCompleted(Long orderId, Long customerId) {
        sleep(SIMULATED_LATENCY_MS);
        log.info("[Notification] 주문 완료 알림 발송 orderId={} customerId={}", orderId, customerId);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
