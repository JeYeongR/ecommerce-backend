package com.ecommerce.backend.order.service;

import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.order.dto.OrderCreateRequest;
import com.ecommerce.backend.order.dto.OrderItemRequest;
import com.ecommerce.backend.order.repository.OrderRepository;
import com.ecommerce.backend.common.domain.Money;
import com.ecommerce.backend.product.domain.Product;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.domain.ProductStatus;
import com.ecommerce.backend.product.repository.ProductOptionRepository;
import com.ecommerce.backend.product.repository.ProductRepository;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("benchmark")
@SpringBootTest
class OrderCreateLatencyBenchmarkTest {

    private static final int SAMPLE_SIZE = 30;

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

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        Customer customer = customerRepository.save(Customer.builder()
            .email("latency-customer-" + suffix + "@test.com")
            .password("password")
            .name("레이턴시테스트")
            .nickname("latency")
            .build());
        customerId = customer.getId();

        Seller seller = sellerRepository.save(Seller.builder()
            .email("latency-seller-" + suffix + "@test.com")
            .password("password")
            .shopName("레이턴시샵")
            .build());

        Product product = Product.builder()
            .seller(seller)
            .name("레이턴시상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(10000))
            .status(ProductStatus.ON_SALE)
            .build();
        product.addOption(ProductOption.builder()
            .optionName("레이턴시옵션")
            .additionalPrice(Money.zero())
            .stock(SAMPLE_SIZE * 10)
            .build());
        product = productRepository.save(product);

        productId = product.getId();
        optionId = product.getOptions().get(0).getId();
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
    void 주문_생성_응답시간을_측정한다() {
        OrderCreateRequest request = new OrderCreateRequest(
            List.of(new OrderItemRequest(optionId, 1))
        );

        long[] latenciesMs = new long[SAMPLE_SIZE];
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            long start = System.nanoTime();
            orderService.create(customerId, request);
            latenciesMs[i] = (System.nanoTime() - start) / 1_000_000;
        }

        long[] sorted = latenciesMs.clone();
        java.util.Arrays.sort(sorted);
        double avg = java.util.Arrays.stream(latenciesMs).average().orElse(0);

        System.out.printf(
            "[BENCH-LATENCY] samples=%d avgMs=%.1f p50=%dms p95=%dms p99=%dms maxMs=%d%n",
            SAMPLE_SIZE, avg,
            percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99), sorted[sorted.length - 1]
        );
    }

    private long percentile(long[] sortedMs, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sortedMs.length) - 1;
        return sortedMs[Math.max(0, Math.min(idx, sortedMs.length - 1))];
    }
}
