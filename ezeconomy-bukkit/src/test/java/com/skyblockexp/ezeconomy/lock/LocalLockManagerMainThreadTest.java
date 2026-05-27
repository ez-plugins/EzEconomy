package com.skyblockexp.ezeconomy.lock;

import com.skyblockexp.ezeconomy.storage.TransferLockManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLockManagerMainThreadTest {
    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acquireReturnsQuicklyWhenPrimaryThreadAndLockHeld() throws Exception {
        UUID id = UUID.randomUUID();
        Thread holder = new Thread(() -> {
            ReentrantLock lock = TransferLockManager.getLock(id);
            lock.lock();
            try {
                try {
                    Thread.sleep(750L);
                } catch (InterruptedException ignored) {
                }
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        Thread.sleep(50L);

        LocalLockManager lm = new LocalLockManager();
        long start = System.nanoTime();
        String token = lm.acquire(id, 5000, 50, 1000);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertNull(token, "Expected null when lock is currently held by another thread");
        assertTrue(elapsedMs < 250, "Acquire should not block the server thread with sleep-retry loops");
        holder.join();
        if (token != null) {
            lm.release(id, token);
        }
    }
}
