package com.ecommerce.backend.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.order.dto.OrderCreateRequest;
import com.ecommerce.backend.order.dto.OrderItemRequest;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.product.domain.Product;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.domain.ProductStatus;
import com.ecommerce.backend.product.repository.ProductOptionRepository;
import com.ecommerce.backend.product.repository.ProductRepository;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Long customerId;
    private Long productId;
    private Long optionId;
    private Long secondOptionId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        Customer customer = customerRepository.save(Customer.builder()
            .email("concurrency-customer-" + suffix + "@test.com")
            .password("password")
            .name("동시성테스트")
            .nickname("concurrency")
            .build());
        customerId = customer.getId();

        Seller seller = sellerRepository.save(Seller.builder()
            .email("concurrency-seller-" + suffix + "@test.com")
            .password("password")
            .shopName("동시성샵")
            .build());

        Product product = Product.builder()
            .seller(seller)
            .name("동시성상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(10000))
            .status(ProductStatus.ON_SALE)
            .build();
        product.addOption(ProductOption.builder()
            .optionName("단일옵션")
            .additionalPrice(Money.zero())
            .stock(1)
            .build());
        product.addOption(ProductOption.builder()
            .optionName("두번째옵션")
            .additionalPrice(Money.zero())
            .stock(5)
            .build());
        product = productRepository.save(product);

        productId = product.getId();
        optionId = product.getOptions().get(0).getId();
        secondOptionId = product.getOptions().get(1).getId();
    }

    @AfterEach
    void tearDown() {
        orderRepository.findAll().stream()
            .filter(order -> order.getCustomer().getId().equals(customerId))
            .forEach(orderRepository::delete);
        productRepository.deleteById(productId);
        customerRepository.deleteById(customerId);
    }

    @Test
    void 재고_1개인_옵션에_동시_주문_10건이_들어오면_1건만_성공한다() throws InterruptedException {
        OrderCreateRequest request = new OrderCreateRequest(
            List.of(new OrderItemRequest(optionId, 1))
        );

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger outOfStockCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderService.create(customerId, request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.OUT_OF_STOCK) {
                        outOfStockCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(outOfStockCount.get()).isEqualTo(THREAD_COUNT - 1);

        ProductOption option = productOptionRepository.findById(optionId).orElseThrow();
        assertThat(option.getStock()).isEqualTo(0);
    }

    @Test
    void 한_주문에_옵션_두개_담아도_둘_다_정상적으로_잠기고_차감된다() {
        OrderCreateRequest request = new OrderCreateRequest(List.of(
            new OrderItemRequest(optionId, 1),
            new OrderItemRequest(secondOptionId, 2)
        ));

        orderService.create(customerId, request);

        ProductOption first = productOptionRepository.findById(optionId).orElseThrow();
        ProductOption second = productOptionRepository.findById(secondOptionId).orElseThrow();
        assertThat(first.getStock()).isEqualTo(0);
        assertThat(second.getStock()).isEqualTo(3);
    }
}
