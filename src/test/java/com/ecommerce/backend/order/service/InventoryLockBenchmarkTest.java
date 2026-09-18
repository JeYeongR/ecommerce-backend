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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 같은 재고 옵션에 몰리는 동시 주문의 처리량/지연을 측정한다.
 * 회귀 테스트가 아니라 Before/After 수치 비교용 — 기록 후 스레드 수만 바꿔 재실행한다.
 * 실행: ./gradlew test --tests InventoryLockBenchmarkTest
 */
@Tag("benchmark")
@SpringBootTest
class InventoryLockBenchmarkTest {

    private static final int[] THREAD_COUNTS = {100, 400, 800};
    private static final int STOCK_PER_RUN = 100_000;

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
            .email("bench-customer-" + suffix + "@test.com")
            .password("password")
            .name("벤치테스트")
            .nickname("bench")
            .build());
        customerId = customer.getId();

        Seller seller = sellerRepository.save(Seller.builder()
            .email("bench-seller-" + suffix + "@test.com")
            .password("password")
            .shopName("벤치샵")
            .build());

        Product product = Product.builder()
            .seller(seller)
            .name("벤치상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(10000))
            .status(ProductStatus.ON_SALE)
            .build();
        product.addOption(ProductOption.builder()
            .optionName("벤치옵션")
            .additionalPrice(Money.zero())
            .stock(STOCK_PER_RUN * THREAD_COUNTS.length)
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
    void 동시_주문_처리량과_지연시간을_스레드수별로_측정한다() throws InterruptedException {
        for (int threadCount : THREAD_COUNTS) {
            runOnce(threadCount);
        }
    }

    private void runOnce(int threadCount) throws InterruptedException {
        OrderCreateRequest request = new OrderCreateRequest(
            List.of(new OrderItemRequest(optionId, 1))
        );

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        List<AtomicLong> latenciesNanos = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            latenciesNanos.add(new AtomicLong());
        }

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    orderService.create(customerId, request);
                    latenciesNanos.get(idx).set(System.nanoTime() - start);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        long wallStart = System.nanoTime();
        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        long wallElapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
        executor.shutdown();

        long[] sortedMs = latenciesNanos.stream()
            .mapToLong(AtomicLong::get)
            .filter(v -> v > 0)
            .map(v -> v / 1_000_000)
            .sorted()
            .toArray();

        double tps = successCount.get() / Math.max(wallElapsedMs / 1000.0, 0.001);

        System.out.printf(
            "[BENCH] threads=%d success=%d fail=%d wallMs=%d tps=%.1f p50=%dms p95=%dms p99=%dms%n",
            threadCount, successCount.get(), failureCount.get(), wallElapsedMs, tps,
            percentile(sortedMs, 50), percentile(sortedMs, 95), percentile(sortedMs, 99)
        );
    }

    private long percentile(long[] sortedMs, int p) {
        if (sortedMs.length == 0) {
            return -1;
        }
        int idx = (int) Math.ceil(p / 100.0 * sortedMs.length) - 1;
        return sortedMs[Math.max(0, Math.min(idx, sortedMs.length - 1))];
    }
}
