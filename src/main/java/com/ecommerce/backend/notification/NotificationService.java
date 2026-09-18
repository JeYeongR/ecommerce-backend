package com.ecommerce.backend.notification;

import com.ecommerce.backend.order.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    private static final long SIMULATED_LATENCY_MS = 150;

    public void notifyOrderCompleted(Order order) {
        sleep(SIMULATED_LATENCY_MS);
        log.info("[Notification] 주문 완료 알림 발송 orderId={} customerId={}",
            order.getId(), order.getCustomer().getId());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
