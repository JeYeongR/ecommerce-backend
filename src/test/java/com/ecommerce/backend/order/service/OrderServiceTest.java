package com.ecommerce.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.concurrent.RedisLockManager;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.order.domain.Order;
import com.ecommerce.backend.order.domain.OrderItem;
import com.ecommerce.backend.order.domain.OrderStatus;
import com.ecommerce.backend.order.dto.OrderCreateRequest;
import com.ecommerce.backend.order.dto.OrderItemRequest;
import com.ecommerce.backend.order.dto.OrderResponse;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.product.domain.Product;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.domain.ProductStatus;
import com.ecommerce.backend.product.repository.ProductOptionRepository;
import com.ecommerce.backend.seller.domain.Seller;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RedisLockManager redisLockManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private OrderPaymentService orderPaymentService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productOptionRepository, customerRepository, redisLockManager, transactionTemplate, orderPaymentService);
    }

    private void stubLockAndTransactionPassthrough() {
        given(redisLockManager.withLock(anyString(), any())).willAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
        stubTransactionPassthrough();
    }

    private void stubTransactionPassthrough() {
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    /** save가 id 100을 부여하고, 결제 후 재조회(findById)에도 같은 주문이 돌아오게 한다. */
    private void stubSaveAndReload() {
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            given(orderRepository.findById(100L)).willReturn(Optional.of(order));
            return order;
        });
    }

    private Customer customer(Long id) {
        return Customer.builder()
            .id(id)
            .email("c@test.com")
            .password("encoded")
            .name("이름")
            .nickname("닉네임")
            .build();
    }

    private ProductOption option(Long id, int stock) {
        Seller seller = Seller.builder()
            .id(1L)
            .email("s@test.com")
            .password("encoded")
            .shopName("샵")
            .build();
        Product product = Product.builder()
            .id(1L)
            .seller(seller)
            .name("상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(1000))
            .status(ProductStatus.ON_SALE)
            .build();
        ProductOption option = ProductOption.builder()
            .id(id)
            .optionName("옵션")
            .additionalPrice(Money.zero())
            .stock(stock)
            .build();
        product.addOption(option);
        return option;
    }

    @Test
    void 주문_생성_성공() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 5);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of(option));
        stubSaveAndReload();

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        OrderResponse response = orderService.create(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.items()).hasSize(1);
        assertThat(option.getStock()).isEqualTo(3);

        verify(orderPaymentService).pay(100L, Money.of(2000));
    }

    @Test
    void 주문_생성_결제가_거절되면_결제실패로_확정하고_재고를_복구한_뒤_예외() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 5);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of(option));
        stubSaveAndReload();
        given(orderPaymentService.pay(any(), any())).willAnswer(invocation -> {
            Order order = orderRepository.findById(100L).orElseThrow();
            ReflectionTestUtils.setField(order, "status", OrderStatus.PAYING);
            return PaymentResult.DECLINED;
        });

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED));
        assertThat(orderRepository.findById(100L).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(option.getStock()).isEqualTo(5);
    }

    @Test
    void 주문_생성_결제_결과를_모르면_결제중_상태로_응답한다() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 5);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of(option));
        stubSaveAndReload();
        given(orderPaymentService.pay(any(), any())).willAnswer(invocation -> {
            Order order = orderRepository.findById(100L).orElseThrow();
            ReflectionTestUtils.setField(order, "status", OrderStatus.PAYING);
            return PaymentResult.UNKNOWN;
        });

        OrderResponse response = orderService.create(1L, new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2))));

        assertThat(response.status()).isEqualTo("PAYING");
        assertThat(option.getStock()).isEqualTo(3);
    }

    @Test
    void 같은_옵션이_여러_번_담기면_수량을_합산해서_하나의_주문항목으로_처리한다() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 10);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of(option));
        stubSaveAndReload();

        OrderCreateRequest request = new OrderCreateRequest(List.of(
            new OrderItemRequest(10L, 3),
            new OrderItemRequest(10L, 3)
        ));

        OrderResponse response = orderService.create(1L, request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(6);
        assertThat(option.getStock()).isEqualTo(4);
    }

    @Test
    void 같은_옵션을_나눠담아도_합산된_수량으로_재고부족_판단한다() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 5);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of(option));

        OrderCreateRequest request = new OrderCreateRequest(List.of(
            new OrderItemRequest(10L, 3),
            new OrderItemRequest(10L, 3)
        ));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));
    }

    @Test
    void 주문_생성_재고부족이면_예외() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 1);
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of(option));

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));
    }

    @Test
    void 주문_생성_존재하지않는_고객이면_예외() {
        stubLockAndTransactionPassthrough();
        given(customerRepository.findById(1L)).willReturn(Optional.empty());

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    void 주문_생성_존재하지않는_옵션이면_예외() {
        stubLockAndTransactionPassthrough();
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer(1L)));
        given(productOptionRepository.findAllById(Set.of(10L))).willReturn(List.of());

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_OPTION_NOT_FOUND));
    }

    @Test
    void 주문_조회_성공() {
        Customer customer = customer(1L);
        Order order = Order.builder()
            .id(100L)
            .customer(customer)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.of(2000))
            .build();
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.get(1L, 100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.customerId()).isEqualTo(1L);
    }

    @Test
    void 주문_조회_존재하지않으면_예외() {
        given(orderRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get(1L, 100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void 주문_조회_타인주문이면_예외() {
        Customer owner = customer(1L);
        Order order = Order.builder()
            .id(100L)
            .customer(owner)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.of(2000))
            .build();
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.get(2L, 100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
    }

    @Test
    void 주문_취소_성공() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 3);
        Order order = Order.builder()
            .id(100L)
            .customer(customer)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.of(2000))
            .build();
        order.addOrderItem(OrderItem.builder()
            .productOption(option)
            .productName(option.getDisplayName())
            .orderPrice(Money.of(1000))
            .quantity(2)
            .build());
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(1L, 100L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(option.getStock()).isEqualTo(5);
    }

    @Test
    void 주문_취소_이미취소된_주문이면_예외() {
        stubLockAndTransactionPassthrough();
        Customer customer = customer(1L);
        ProductOption option = option(10L, 3);
        Order order = Order.builder()
            .id(100L)
            .customer(customer)
            .status(OrderStatus.CANCELLED)
            .totalPrice(Money.of(2000))
            .build();
        order.addOrderItem(OrderItem.builder()
            .productOption(option)
            .productName(option.getDisplayName())
            .orderPrice(Money.of(1000))
            .quantity(2)
            .build());
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L, 100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
    }

    @Test
    void 주문_취소_타인주문이면_예외() {
        stubTransactionPassthrough();
        Customer owner = customer(1L);
        Order order = Order.builder()
            .id(100L)
            .customer(owner)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.of(2000))
            .build();
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(2L, 100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ACCESS_DENIED));
    }

    private Order orderWithItem(OrderStatus status, ProductOption option, int quantity) {
        Order order = Order.builder()
            .id(100L)
            .customer(customer(1L))
            .status(status)
            .totalPrice(Money.of(2000))
            .build();
        order.addOrderItem(OrderItem.builder()
            .productOption(option)
            .productName(option.getDisplayName())
            .orderPrice(Money.of(1000))
            .quantity(quantity)
            .build());
        return order;
    }

    @Test
    void 주문_취소_결제완료_주문이면_예외이고_재고는_그대로다() {
        stubLockAndTransactionPassthrough();
        ProductOption option = option(10L, 3);
        given(orderRepository.findById(100L)).willReturn(Optional.of(orderWithItem(OrderStatus.PAID, option, 2)));

        assertThatThrownBy(() -> orderService.cancel(1L, 100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
        assertThat(option.getStock()).isEqualTo(3);
    }

    @Test
    void 결제_실패_확정이면_PAYMENT_FAILED가_되고_재고가_복구된다() {
        stubLockAndTransactionPassthrough();
        ProductOption option = option(10L, 3);
        Order order = orderWithItem(OrderStatus.PAYING, option, 2);
        given(orderRepository.findById(100L)).willReturn(Optional.of(order));

        orderService.failPayment(100L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(option.getStock()).isEqualTo(5);
    }

    @Test
    void 결제_실패_확정은_결제중이_아니면_예외이고_재고는_그대로다() {
        stubLockAndTransactionPassthrough();
        ProductOption option = option(10L, 3);
        given(orderRepository.findById(100L)).willReturn(Optional.of(orderWithItem(OrderStatus.PENDING, option, 2)));

        assertThatThrownBy(() -> orderService.failPayment(100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATUS));
        assertThat(option.getStock()).isEqualTo(3);
    }
}
