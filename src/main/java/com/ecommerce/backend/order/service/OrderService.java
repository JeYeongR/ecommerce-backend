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
import com.ecommerce.backend.order.dto.OrderItemResponse;
import com.ecommerce.backend.order.dto.OrderResponse;
import com.ecommerce.backend.order.dto.OrderSummaryResponse;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.product.domain.Money;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.repository.ProductOptionRepository;
import java.util.List;
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

        Order order = Order.builder()
            .customer(customer)
            .status(OrderStatus.PENDING)
            .totalPrice(Money.zero())
            .build();

        Money totalPrice = Money.zero();
        for (var item : request.items()) {
            ProductOption option = findOption(item.productOptionId());
            int quantity = item.quantity();

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

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        for (OrderItem item : order.getOrderItems()) {
            item.getProductOption().increaseStock(item.getQuantity());
        }
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

    private ProductOption findOption(Long productOptionId) {
        return productOptionRepository.findById(productOptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND));
    }
}
