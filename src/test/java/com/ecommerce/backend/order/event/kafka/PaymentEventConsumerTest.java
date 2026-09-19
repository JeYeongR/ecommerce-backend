package com.ecommerce.backend.order.event.kafka;

import static org.mockito.Mockito.verify;

import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentEventConsumer consumer;

    @Test
    void 메시지를_받으면_주문ID와_결제금액으로_결제_서비스를_호출한다() {
        consumer.onOrderCompleted(new OrderCompletedMessage(1L, 2L, 10000));

        verify(paymentService).capturePayment(1L, Money.of(10000));
    }
}
