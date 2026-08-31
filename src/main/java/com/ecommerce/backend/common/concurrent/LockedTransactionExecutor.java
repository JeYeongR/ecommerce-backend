package com.ecommerce.backend.common.concurrent;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class LockedTransactionExecutor {

    private final RedisLockManager redisLockManager;
    private final TransactionTemplate transactionTemplate;

    public <T> T executeWithLock(String key, Supplier<T> action) {
        return redisLockManager.withLock(key, () -> transactionTemplate.execute(status -> action.get()));
    }
}
