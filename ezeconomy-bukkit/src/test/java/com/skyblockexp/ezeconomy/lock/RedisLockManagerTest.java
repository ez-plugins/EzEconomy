package com.skyblockexp.ezeconomy.lock;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class RedisLockManagerTest {

    /**
     * A small in-memory fake that simulates Redis SET NX + token behavior.
     * This keeps the test fast and removes any Docker/Testcontainers dependency.
     */
    public static class FakeRedisLockManager implements LockManager, AutoCloseable {
        private final Map<UUID, String> store = new ConcurrentHashMap<>();

        @Override
        public String acquire(UUID uuid, long ttlMs, long retryMs, int maxAttempts) throws InterruptedException {
            String token = UUID.randomUUID().toString();
            for (int i = 0; i < maxAttempts; i++) {
                if (store.putIfAbsent(uuid, token) == null) {
                    return token;
                }
                Thread.sleep(Math.min(retryMs, 50));
            }
            return null;
        }

        @Override
        public boolean release(UUID uuid, String token) {
            return store.remove(uuid, token);
        }

        @Override
        public void close() { /* no-op */ }
    }

    @Test
    public void acquireAndReleaseLockWorks_mocked() throws Exception {
        try (FakeRedisLockManager lm = new FakeRedisLockManager()) {
            UUID id = UUID.randomUUID();
            String token = lm.acquire(id, 3000, 50, 10);
            assertNotNull(token, "Expected to acquire lock token");

            // Second acquire without release should fail (immediate attempts)
            String token2 = lm.acquire(id, 3000, 10, 3);
            assertNull(token2, "Should not acquire lock when already held");

            boolean released = lm.release(id, token);
            assertTrue(released, "Expected release to succeed");

            // After release, should be able to acquire again
            String token3 = lm.acquire(id, 3000, 50, 10);
            assertNotNull(token3, "Expected to acquire lock after release");
            lm.release(id, token3);
        }
    }

    @Test
    public void acquireOrderedReleasesPreviouslyAcquiredOnFailure() throws Exception {
        try (FakeRedisLockManager lm = new FakeRedisLockManager()) {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();

            // pre-lock the second UUID so the ordered acquire will fail on the second
            String tokenSecond = lm.acquire(second, 3000, 10, 10);
            assertNotNull(tokenSecond, "setup: expected to lock second uuid");

            // attempt ordered acquire on [first, second] -> should fail and release first
            String[] tokens = lm.acquireOrdered(new UUID[]{first, second}, 3000, 10, 5);
            assertNull(tokens, "Expected acquireOrdered to fail when second is already held");

            // ensure first is not left locked
            String t1 = lm.acquire(first, 3000, 10, 3);
            assertNotNull(t1, "First lock should have been released after failure");
            lm.release(first, t1);

            // cleanup
            boolean released = lm.release(second, tokenSecond);
            assertTrue(released, "Expected to release pre-locked second token");
        }
    }

    @Test
    public void transferLockManagerActsAsMutex() throws Exception {
        java.util.UUID id = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        Runnable task = () -> {
            java.util.concurrent.locks.ReentrantLock l = com.skyblockexp.ezeconomy.storage.TransferLockManager.getLock(id);
            for (int i = 0; i < 5000; i++) {
                l.lock();
                try {
                    counter.incrementAndGet();
                } finally {
                    l.unlock();
                }
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start(); b.start();
        a.join(); b.join();
        assertEquals(10000, counter.get(), "Counter should be exactly 10000 when protected by TransferLockManager locks");
    }

    @Test
    public void acquireThrowsRuntimeExceptionIsPropagated() {
        class ThrowingAcquireLockManager implements LockManager {
            @Override
            public String acquire(UUID uuid, long ttlMs, long retryMs, int maxAttempts) {
                throw new RuntimeException("redis down");
            }

            @Override
            public boolean release(UUID uuid, String token) { return false; }
        }

        LockManager lm = new ThrowingAcquireLockManager();
        UUID id = UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> lm.acquire(id, 1000, 10, 1));
    }

    @Test
    public void acquireRespectsMaxAttemptsAndReturnsNullWhenBusy() throws Exception {
        try (FakeRedisLockManager lm = new FakeRedisLockManager()) {
            UUID id = UUID.randomUUID();
            // pre-lock the id
            String pre = lm.acquire(id, 3000, 10, 10);
            assertNotNull(pre);

            // now a new acquire with very small attempts should return null quickly
            String t = lm.acquire(id, 3000, 5, 1);
            assertNull(t, "Expected acquire to return null when resource busy and maxAttempts small");

            // cleanup
            assertTrue(lm.release(id, pre));
        }
    }

    @Test
    public void releaseOrderedSwallowsExceptions() {
        class ThrowingReleaseLockManager implements LockManager {
            @Override
            public String acquire(UUID uuid, long ttlMs, long retryMs, int maxAttempts) { return "tok"; }

            @Override
            public boolean release(UUID uuid, String token) { throw new RuntimeException("release failed"); }
        }

        LockManager lm = new ThrowingReleaseLockManager();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // Should not throw
        lm.releaseOrdered(new UUID[]{a,b}, new String[]{"t1","t2"});
    }
}
