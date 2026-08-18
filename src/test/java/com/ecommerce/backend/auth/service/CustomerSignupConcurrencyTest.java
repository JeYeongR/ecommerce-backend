package com.ecommerce.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.backend.auth.dto.SignupRequest;
import com.ecommerce.backend.common.BusinessException;
import com.ecommerce.backend.common.ErrorCode;
import com.ecommerce.backend.customer.repository.CustomerRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CustomerSignupConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private CustomerAuthService customerAuthService;

    @Autowired
    private CustomerRepository customerRepository;

    private String email;

    @AfterEach
    void tearDown() {
        customerRepository.findByEmail(email).ifPresent(customerRepository::delete);
    }

    @Test
    void 같은_이메일로_동시_가입_10건이_들어오면_1건만_성공한다() throws InterruptedException {
        email = "concurrency-signup-" + UUID.randomUUID() + "@test.com";
        SignupRequest request = new SignupRequest(email, "password", "이름", "닉네임");

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    customerAuthService.signup(request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.EMAIL_ALREADY_EXISTS) {
                        duplicateCount.incrementAndGet();
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
        assertThat(duplicateCount.get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(customerRepository.findByEmail(email)).isPresent();
    }
}
