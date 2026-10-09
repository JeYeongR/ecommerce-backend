package com.ecommerce.backend.order.service;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.PageResponse;
import com.ecommerce.backend.common.concurrent.RedisLockManager;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.order.domain.Order;
import com.ecommerce.backend.order.domain.OrderItem;
import com.ecommerce.backend.order.domain.OrderStatus;
import com.ecommerce.backend.order.dto.OrderCreateRequest;
import com.ecommerce.backend.order.dto.OrderItemRequest;
import com.ecommerce.backend.order.dto.OrderItemResponse;
import com.ecommerce.backend.order.dto.OrderResponse;
import com.ecommerce.backend.order.dto.OrderSummaryResponse;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.repository.ProductOptionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductOptionRepository productOptionRepository;
    private final CustomerRepository customerRepository;
    private final RedisLockManager redisLockManager;
    private final TransactionTemplate transactionTemplate;
    private final OrderPaymentService orderPaymentService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse create(Long customerId, OrderCreateRequest request) {
        Map<Long, Integer> quantities = request.items().stream()
            .collect(Collectors.groupingBy(OrderItemRequest::productOptionId, Collectors.summingInt(OrderItemRequest::quantity)));
        List<Long> optionIds = quantities.keySet().stream().sorted().toList();

        OrderResponse created = withOptionLocks(optionIds, () ->
            transactionTemplate.execute(status -> doCreate(customerId, quantities)));

        // 결제는 주문 트랜잭션과 재고 락 밖에서, 같은 요청 안에서 동기로 처리한다.
        PaymentResult result = orderPaymentService.pay(created.id(), Money.of(created.totalPrice()));
        if (result == PaymentResult.DECLINED) {
            failPayment(created.id());
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        // PAID면 완료, 결과 불명이면 결제중(PAYING) 상태 그대로 돌려준다.
        return transactionTemplate.execute(status -> toResponse(findOrder(created.id())));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
            .map(OrderItemResponse::from)
            .toList();
        return OrderResponse.of(order, items);
    }

    private OrderResponse doCreate(Long customerId, Map<Long, Integer> quantities) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        Map<Long, ProductOption> options = findOptions(quantities.keySet());

        Order order = Order.builder()
            .customer(customer)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.zero())
            .build();

        Money totalPrice = Money.zero();
        for (var entry : quantities.entrySet()) {
            ProductOption option = options.get(entry.getKey());
            int quantity = entry.getValue();

            if (option.getStock() < quantity) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }

            Money orderPrice = option.getProduct()
                    .getBasePrice()
                    .add(option.getAdditionalPrice());
            totalPrice = totalPrice.add(orderPrice.multiply(quantity));

            order.addOrderItem(OrderItem.builder()
                .productOption(option)
                .productName(option.getDisplayName())
                .orderPrice(orderPrice)
                .quantity(quantity)
                .build());

            option.decreaseStock(quantity);
        }
        order.updateTotalPrice(totalPrice);
        orderRepository.save(order);

        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return OrderResponse.of(order, itemResponses);
    }

    private Map<Long, ProductOption> findOptions(Set<Long> optionIds) {
        List<ProductOption> found = productOptionRepository.findAllById(optionIds);
        if (found.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }
        return found.stream().collect(Collectors.toMap(ProductOption::getId, Function.identity()));
    }

    private <T> T withOptionLocks(List<Long> optionIds, Supplier<T> action) {
        if (optionIds.isEmpty()) {
            return action.get();
        }
        Long first = optionIds.get(0);
        List<Long> rest = optionIds.subList(1, optionIds.size());
        return redisLockManager.withLock("stock:" + first, () -> withOptionLocks(rest, action));
    }

    public PageResponse<OrderSummaryResponse> list(Long customerId, Pageable pageable) {
        Page<OrderSummaryResponse> responses = orderRepository.findByCustomerId(customerId, pageable)
                .map(OrderSummaryResponse::from);

        return PageResponse.from(responses);
    }

    public OrderResponse get(Long customerId, Long orderId) {
        Order order = findOwnedOrder(customerId, orderId);

        List<OrderItemResponse> items = order.getOrderItems().stream()
            .map(OrderItemResponse::from)
            .toList();

        return OrderResponse.of(order, items);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse cancel(Long customerId, Long orderId) {
        List<Long> optionIds = transactionTemplate.execute(status -> lockKeysOf(findOwnedOrder(customerId, orderId)));

        return withOptionLocks(optionIds, () ->
            transactionTemplate.execute(status -> doCancel(customerId, orderId)));
    }

    /** 결제 실패 확정: 주문과 결제는 함께 움직이므로 PAYMENT_FAILED로 바꾸면서 재고도 같은 트랜잭션에서 복구한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void failPayment(Long orderId) {
        List<Long> optionIds = transactionTemplate.execute(status -> lockKeysOf(findOrder(orderId)));

        withOptionLocks(optionIds, () -> transactionTemplate.execute(status -> {
            Order order = findOrder(orderId);
            order.markPaymentFailed();
            restoreStock(order);
            return null;
        }));
    }

    private List<Long> lockKeysOf(Order order) {
        return order.getOrderItems().stream()
            .map(item -> item.getProductOption().getId())
            .distinct()
            .sorted()
            .toList();
    }

    private OrderResponse doCancel(Long customerId, Long orderId) {
        Order order = findOwnedOrder(customerId, orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        order.cancel();
        restoreStock(order);

        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return OrderResponse.of(order, items);
    }

    private void restoreStock(Order order) {
        order.getOrderItems().forEach(item -> item.getProductOption().increaseStock(item.getQuantity()));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order findOwnedOrder(Long customerId, Long orderId) {
        Order order = findOrder(orderId);

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return order;
    }
}
