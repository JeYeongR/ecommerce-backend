package com.ecommerce.backend.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.domain.Money;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class OrderTest {

    private Order order(OrderStatus status) {
        return Order.builder().status(status).totalPrice(Money.of(1000)).build();
    }

    private void assertAllowedOnly(Set<OrderStatus> allowed, OrderStatus expectedAfter, Consumer<Order> action) {
        for (OrderStatus status : OrderStatus.values()) {
            Order order = order(status);
            if (allowed.contains(status)) {
                action.accept(order);
                assertThat(order.getStatus()).as("from %s", status).isEqualTo(expectedAfter);
            } else {
                assertThatThrownBy(() -> action.accept(order))
                    .as("from %s", status)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
                assertThat(order.getStatus()).isEqualTo(status);
            }
        }
    }

    @Test
    void 취소는_접수_상태에서만_가능하다() {
        assertAllowedOnly(EnumSet.of(OrderStatus.PENDING), OrderStatus.CANCELLED, Order::cancel);
    }

    @Test
    void 결제_완료는_결제중에서만_가능하다() {
        assertAllowedOnly(EnumSet.of(OrderStatus.PAYING), OrderStatus.PAID, Order::markPaid);
    }

    @Test
    void 결제_실패는_결제중에서만_가능하다() {
        assertAllowedOnly(EnumSet.of(OrderStatus.PAYING), OrderStatus.PAYMENT_FAILED, Order::markPaymentFailed);
    }
}
