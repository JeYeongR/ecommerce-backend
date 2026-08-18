package com.ecommerce.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private CustomerRepository customerRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productOptionRepository, customerRepository);
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
        Customer customer = customer(1L);
        ProductOption option = option(10L, 5);
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of(option));
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        OrderResponse response = orderService.create(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.items()).hasSize(1);
        assertThat(option.getStock()).isEqualTo(3);
    }

    @Test
    void 같은_옵션이_여러_번_담기면_수량을_합산해서_하나의_주문항목으로_처리한다() {
        Customer customer = customer(1L);
        ProductOption option = option(10L, 10);
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of(option));
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

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
        ProductOption option = option(10L, 5);
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of(option));
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer(1L)));

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
        ProductOption option = option(10L, 1);
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of(option));
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer(1L)));

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));
    }

    @Test
    void 주문_생성_존재하지않는_고객이면_예외() {
        given(customerRepository.findById(1L)).willReturn(Optional.empty());

        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(10L, 2)));

        assertThatThrownBy(() -> orderService.create(1L, request))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    void 주문_생성_존재하지않는_옵션이면_예외() {
        given(customerRepository.findById(1L)).willReturn(Optional.of(customer(1L)));
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of());

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
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of(option));

        OrderResponse response = orderService.cancel(1L, 100L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(option.getStock()).isEqualTo(5);
    }

    @Test
    void 주문_취소_이미취소된_주문이면_예외() {
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
        given(productOptionRepository.findAllByIdInForUpdate(List.of(10L))).willReturn(List.of(option));

        assertThatThrownBy(() -> orderService.cancel(1L, 100L))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
    }

    @Test
    void 주문_취소_타인주문이면_예외() {
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
}
