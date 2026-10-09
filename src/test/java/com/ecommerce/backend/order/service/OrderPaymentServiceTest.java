package com.ecommerce.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.order.domain.Order;
import com.ecommerce.backend.order.domain.OrderStatus;
import com.ecommerce.backend.order.event.OrderEventOutbox;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.payment.PaymentDeclinedException;
import com.ecommerce.backend.payment.PaymentService;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private OrderEventOutbox orderEventOutbox;

    private OrderPaymentService service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentService(orderRepository, paymentService, transactionTemplate, orderEventOutbox);
    }

    private void stubTransactions() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Order payingOrder() {
        return Order.builder().id(1L).status(OrderStatus.PAYING).totalPrice(Money.of(1000)).build();
    }

    @Test
    void 선점에_성공하면_결제하고_PAID로_바꾸고_완료_이벤트를_기록한다() {
        stubTransactions();
        Order order = payingOrder();
        given(orderRepository.claimForPayment(1L)).willReturn(1);
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        PaymentResult result = service.pay(1L, Money.of(1000));

        assertThat(result).isEqualTo(PaymentResult.PAID);
        verify(paymentService).capturePayment(1L, Money.of(1000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderEventOutbox).record(order);
    }

    @Test
    void 선점에_실패하면_결제하지_않는다() {
        stubTransactions();
        given(orderRepository.claimForPayment(1L)).willReturn(0);

        PaymentResult result = service.pay(1L, Money.of(1000));

        assertThat(result).isEqualTo(PaymentResult.SKIPPED);
        verify(paymentService, never()).capturePayment(any(), any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void PG가_결제를_거절하면_DECLINED를_돌려주고_이벤트를_기록하지_않는다() {
        stubTransactions();
        given(orderRepository.claimForPayment(1L)).willReturn(1);
        doThrow(new PaymentDeclinedException("card declined")).when(paymentService).capturePayment(any(), any());

        PaymentResult result = service.pay(1L, Money.of(1000));

        assertThat(result).isEqualTo(PaymentResult.DECLINED);
        verify(orderRepository, never()).findById(any());
        verify(orderEventOutbox, never()).record(any());
    }

    @Test
    void PG_호출이_예외면_결제중으로_남기고_예외를_밖으로_던지지_않는다() {
        stubTransactions();
        Order order = payingOrder();
        given(orderRepository.claimForPayment(1L)).willReturn(1);
        doThrow(new IllegalStateException("pg timeout")).when(paymentService).capturePayment(any(), any());

        PaymentResult result = service.pay(1L, Money.of(1000));

        assertThat(result).isEqualTo(PaymentResult.UNKNOWN);
        verify(orderRepository, never()).findById(any());
        verify(orderEventOutbox, never()).record(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYING);
    }
}
