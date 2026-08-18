package com.ecommerce.backend.common.concurrent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class KeyLockManager {

    private final ConcurrentHashMap<Object, Entry> locks = new ConcurrentHashMap<>();

    public <T> T withLock(Object key, Supplier<T> action) {
        Entry entry = locks.compute(key, (k, existing) -> {
            Entry e = existing != null ? existing : new Entry();
            e.refCount.incrementAndGet();
            return e;
        });

        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            locks.compute(key, (k, existing) -> {
                if (existing == entry && entry.refCount.decrementAndGet() == 0) {
                    return null;
                }
                return existing;
            });
        }
    }

    private static class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger refCount = new AtomicInteger();
    }
}
