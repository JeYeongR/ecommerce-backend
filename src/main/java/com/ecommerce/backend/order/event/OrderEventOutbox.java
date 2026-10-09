package com.ecommerce.backend.order.event;

import com.ecommerce.backend.order.domain.Order;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제중(주문 접수) 이벤트를 주문과 같은 트랜잭션에 기록하고, 이후 relay가 at-least-once로 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventOutbox {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventSender sender;

    /** 호출자 트랜잭션에 반드시 참여한다. 트랜잭션 밖이면 예외. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Order order) {
        outboxEventRepository.save(OutboxEvent.builder()
            .orderId(order.getId())
            .customerId(order.getCustomer().getId())
            .totalPriceAmount(order.getTotalPrice().intValue())
            .build());
    }

    // 여러 인스턴스가 동시에 돌아도 SKIP LOCKED로 서로 다른 행을 가져간다.
    // ponytail: 발행하는 동안 행 락 유지(최악 건당 5초), 실패 건은 매초 재시도. 재시도 횟수/FAILED는 필요할 때 추가
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        for (OutboxEvent event : outboxEventRepository.lockPending(BATCH_SIZE)) {
            try {
                sender.send(new OrderCompletedMessage(
                    event.getOrderId(), event.getCustomerId(), event.getTotalPriceAmount()));
                event.markPublished(LocalDateTime.now());
            } catch (RuntimeException e) {
                log.error("[Outbox] 이벤트 발행 실패, 재시도 예정 orderId={}", event.getOrderId(), e);
            }
        }
    }
}
