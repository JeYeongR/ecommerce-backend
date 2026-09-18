package com.ecommerce.backend.order.service;

import com.ecommerce.backend.common.concurrent.RedisLockManager;
import com.ecommerce.backend.customer.domain.Customer;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import com.ecommerce.backend.product.domain.Product;
import com.ecommerce.backend.product.domain.ProductOption;
import com.ecommerce.backend.product.domain.ProductStatus;
import com.ecommerce.backend.product.repository.ProductOptionRepository;
import com.ecommerce.backend.product.repository.ProductRepository;
import com.ecommerce.backend.seller.domain.Seller;
import com.ecommerce.backend.seller.repository.SellerRepository;
import com.ecommerce.backend.common.domain.Money;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "락 잡은 채로 외부 호출(결제 승인 등)이 낀다"는 실제 운영 조건을 시뮬레이션.
 * Redisson: 락 획득 → 슬로우 콜(트랜잭션 밖) → 짧은 트랜잭션으로 재고 차감.
 * hot(재고 API)과 무관한 bystander(다른 API, 같은 HikariCP 풀 공유)를 같이 돌려서
 * "커넥션 풀 고갈이 무관한 API까지 물고 들어가는지"를 본다.
 * 회귀 테스트 아님. 실행: ./gradlew benchmark --tests InventoryLockCollateralDamageBenchmarkTest
 */
@Tag("benchmark")
@SpringBootTest
class InventoryLockCollateralDamageBenchmarkTest {

    private static final int HOT_THREADS = 30;
    private static final int BYSTANDER_THREADS = 20;
    private static final int SLOW_CALL_MS = 150;

    @Autowired
    private RedisLockManager redisLockManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ProductOptionRepository productOptionRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private CustomerRepository customerRepository;

    private Long productId;
    private Long optionId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        Seller seller = sellerRepository.save(Seller.builder()
            .email("collateral-seller-" + suffix + "@test.com")
            .password("password")
            .shopName("콜래트럴샵")
            .build());

        Product product = Product.builder()
            .seller(seller)
            .name("콜래트럴상품")
            .thumbnailUrl("https://example.com/thumb.png")
            .basePrice(Money.of(10000))
            .status(ProductStatus.ON_SALE)
            .build();
        product.addOption(ProductOption.builder()
            .optionName("콜래트럴옵션")
            .additionalPrice(Money.zero())
            .stock(1_000_000)
            .build());
        product = productRepository.save(product);

        productId = product.getId();
        optionId = product.getOptions().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteById(productId);
    }

    @Test
    void 재고락에_슬로우콜이_끼었을때_무관한_API가_커넥션풀_고갈로_영향받는지_측정한다() throws InterruptedException {
        AtomicBoolean running = new AtomicBoolean(true);

        ExecutorService hotExecutor = Executors.newFixedThreadPool(HOT_THREADS);
        ExecutorService bystanderExecutor = Executors.newFixedThreadPool(BYSTANDER_THREADS);

        CountDownLatch hotReady = new CountDownLatch(HOT_THREADS);
        CountDownLatch bystanderReady = new CountDownLatch(BYSTANDER_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch hotDone = new CountDownLatch(HOT_THREADS);
        CountDownLatch bystanderDone = new CountDownLatch(BYSTANDER_THREADS);

        AtomicInteger hotSuccess = new AtomicInteger();
        AtomicInteger hotFail = new AtomicInteger();
        AtomicInteger bystanderSuccess = new AtomicInteger();
        AtomicInteger bystanderFail = new AtomicInteger();
        AtomicLong bystanderMaxLatencyMs = new AtomicLong();

        for (int i = 0; i < HOT_THREADS; i++) {
            hotExecutor.submit(() -> {
                hotReady.countDown();
                try {
                    startLatch.await();
                    for (int j = 0; j < 3; j++) {
                        try {
                            createOneOrder();
                            hotSuccess.incrementAndGet();
                        } catch (Exception e) {
                            hotFail.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    hotDone.countDown();
                }
            });
        }

        for (int i = 0; i < BYSTANDER_THREADS; i++) {
            bystanderExecutor.submit(() -> {
                bystanderReady.countDown();
                try {
                    startLatch.await();
                    while (running.get()) {
                        long start = System.nanoTime();
                        try {
                            customerRepository.count();
                            bystanderSuccess.incrementAndGet();
                        } catch (Exception e) {
                            bystanderFail.incrementAndGet();
                        }
                        long latencyMs = (System.nanoTime() - start) / 1_000_000;
                        bystanderMaxLatencyMs.updateAndGet(prev -> Math.max(prev, latencyMs));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    bystanderDone.countDown();
                }
            });
        }

        hotReady.await();
        bystanderReady.await();
        long wallStart = System.nanoTime();
        startLatch.countDown();

        hotDone.await(180, TimeUnit.SECONDS);
        running.set(false);
        bystanderDone.await(30, TimeUnit.SECONDS);
        long wallElapsedMs = (System.nanoTime() - wallStart) / 1_000_000;

        hotExecutor.shutdown();
        bystanderExecutor.shutdown();

        System.out.printf(
            "[BENCH-COLLATERAL] wallMs=%d hotSuccess=%d hotFail=%d bystanderSuccess=%d bystanderFail=%d bystanderMaxLatencyMs=%d%n",
            wallElapsedMs, hotSuccess.get(), hotFail.get(), bystanderSuccess.get(), bystanderFail.get(), bystanderMaxLatencyMs.get()
        );
    }

    private void createOneOrder() {
        redisLockManager.withLock("stock:" + optionId, () -> {
            sleep(SLOW_CALL_MS);
            return transactionTemplate.execute(status -> {
                ProductOption option = productOptionRepository.findById(optionId).orElseThrow();
                option.decreaseStock(1);
                return null;
            });
        });
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
