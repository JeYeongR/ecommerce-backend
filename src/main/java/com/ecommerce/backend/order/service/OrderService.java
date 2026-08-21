package com.ecommerce.backend.order.service;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.PageResponse;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductOptionRepository productOptionRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public OrderResponse create(Long customerId, OrderCreateRequest request) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        Map<Long, Integer> quantities = request.items().stream()
            .collect(Collectors.groupingBy(OrderItemRequest::productOptionId, Collectors.summingInt(OrderItemRequest::quantity)));

        List<Long> optionIds = quantities.keySet().stream().sorted().toList();
        Map<Long, ProductOption> options = lockOptions(optionIds);

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

    private Map<Long, ProductOption> lockOptions(List<Long> optionIds) {
        List<ProductOption> locked = productOptionRepository.findAllByIdInForUpdate(optionIds);
        if (locked.size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }
        return locked.stream().collect(Collectors.toMap(ProductOption::getId, Function.identity()));
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

    @Transactional
    public OrderResponse cancel(Long customerId, Long orderId) {
        Order order = findOwnedOrder(customerId, orderId);

        List<Long> optionIds = order.getOrderItems().stream()
            .map(item -> item.getProductOption().getId())
            .distinct()
            .sorted()
            .toList();
        lockOptions(optionIds);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        order.getOrderItems().forEach(item -> item.getProductOption().increaseStock(item.getQuantity()));
        order.cancel();

        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return OrderResponse.of(order, items);
    }

    private Order findOwnedOrder(Long customerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return order;
    }
}
