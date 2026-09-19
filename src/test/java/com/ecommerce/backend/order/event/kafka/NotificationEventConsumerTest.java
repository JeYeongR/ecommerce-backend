package com.ecommerce.backend.order.event.kafka;

import static org.mockito.Mockito.verify;

import com.ecommerce.backend.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Test
    void 메시지를_받으면_주문ID와_고객ID로_알림_서비스를_호출한다() {
        consumer.onOrderCompleted(new OrderCompletedMessage(1L, 2L, 10000));

        verify(notificationService).notifyOrderCompleted(1L, 2L);
    }
}
