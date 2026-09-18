package com.ecommerce.backend.order.service;

import com.ecommerce.backend.EcommerceBackendApplication;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 앱 인스턴스 3개를 실제로 띄워(각자 own HikariCP pool + own RedissonClient) 같은 재고 옵션에
 * 동시 주문을 흘려보낸다. 단일 인스턴스 벤치(InventoryLockBenchmarkTest)로는 재현 안 되는
 * "여러 인스턴스가 DB row lock/커넥션 풀을 같이 두고 경합" 상황을 잡기 위함.
 * 회귀 테스트 아님. 실행: ./gradlew test --tests InventoryLockMultiInstanceBenchmarkTest
 */
@Tag("benchmark")
class InventoryLockMultiInstanceBenchmarkTest {

    private static final int INSTANCE_COUNT = 3;
    private static final int[] THREAD_COUNTS = {1500, 3000, 5000};

    @Test
    void 인스턴스_3개에서_동시_주문_처리량과_지연시간을_측정한다() throws Exception {
        List<ConfigurableApplicationContext> contexts = new ArrayList<>();
        try {
            for (int i = 0; i < INSTANCE_COUNT; i++) {
                contexts.add(new SpringApplicationBuilder(EcommerceBackendApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .properties(
                        "spring.jmx.enabled=false",
                        "server.port=0",
                        "spring.datasource.hikari.maximum-pool-size=5")
                    .run());
            }

            ConfigurableApplicationContext seedCtx = contexts.get(0);
            SeedData seed = seedData(seedCtx);

            for (int threadCount : THREAD_COUNTS) {
                runOnce(contexts, seed, threadCount);
            }

            tearDown(seedCtx, seed);
        } finally {
            contexts.forEach(ConfigurableApplicationContext::close);
        }
    }

    private record SeedData(Long customerId, Long productId, Long optionId) {
    }

    private SeedData seedData(ConfigurableApplicationContext ctx) {
        CustomerRepository customerRepository = ctx.getBean(CustomerRepository.class);
        SellerRepository sellerRepository = ctx.getBean(SellerRepository.class);
        ProductRepository productRepository = ctx.getBean(ProductRepository.class);

        String suffix = UUID.randomUUID().toString();

        Customer customer = customerRepository.save(Customer.builder()
            .email("multi-bench-customer-" + suffix + "@test.com")
            .password("password")
            .name("멀티벤치")
            .nickname("multibench")
            .build());

        Seller seller = sellerRepository.save(Seller.builder()
            .email("multi-bench-seller-" + suffix + "@test.com")
            .password("password")
            .shopName("멀티벤치샵")
            .build());

        Product product = Product.builder()
            .seller(seller)
            .name("멀티벤치상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(10000))
            .status(ProductStatus.ON_SALE)
            .build();
        product.addOption(ProductOption.builder()
            .optionName("멀티벤치옵션")
            .additionalPrice(Money.zero())
            .stock(1_000_000)
            .build());
        product = productRepository.save(product);

        return new SeedData(customer.getId(), product.getId(), product.getOptions().get(0).getId());
    }

    private void tearDown(ConfigurableApplicationContext ctx, SeedData seed) {
        OrderRepository orderRepository = ctx.getBean(OrderRepository.class);
        ProductRepository productRepository = ctx.getBean(ProductRepository.class);
        CustomerRepository customerRepository = ctx.getBean(CustomerRepository.class);

        orderRepository.findAll().stream()
            .filter(order -> order.getCustomer().getId().equals(seed.customerId()))
            .forEach(orderRepository::delete);
        productRepository.deleteById(seed.productId());
        customerRepository.deleteById(seed.customerId());
    }

    private void runOnce(List<ConfigurableApplicationContext> contexts, SeedData seed, int threadCount) throws InterruptedException {
        List<OrderService> orderServices = contexts.stream()
            .map(ctx -> ctx.getBean(OrderService.class))
            .toList();

        OrderCreateRequest request = new OrderCreateRequest(
            List.of(new OrderItemRequest(seed.optionId(), 1))
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
            OrderService orderService = orderServices.get(i % orderServices.size());
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    orderService.create(seed.customerId(), request);
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
        doneLatch.await(300, TimeUnit.SECONDS);
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
            "[BENCH-MULTI] instances=%d threads=%d success=%d fail=%d wallMs=%d tps=%.1f p50=%dms p95=%dms p99=%dms%n",
            contexts.size(), threadCount, successCount.get(), failureCount.get(), wallElapsedMs, tps,
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
