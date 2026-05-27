package com.skyblockexp.ezeconomy.lock;

import com.skyblockexp.ezeconomy.storage.TransferLockManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class LocalLockManagerTest {

    @Test
    void acquireAndReleaseSucceeds() throws InterruptedException {
        UUID id = UUID.randomUUID();
        LocalLockManager lm = new LocalLockManager();

        String token = lm.acquire(id, 1000, 10, 3);
        assertNotNull(token, "Should acquire lock when free");

        boolean released = lm.release(id, token);
        assertTrue(released, "Should release previously acquired lock");
    }

    @Test
    void acquireFailsWhenLockHeld() throws InterruptedException {
        UUID id = UUID.randomUUID();
        // Lock the underlying lock in a separate thread to simulate another owner
        Thread holder = new Thread(() -> {
            ReentrantLock l = TransferLockManager.getLock(id);
            l.lock();
            try {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            } finally {
                l.unlock();
            }
        });
        holder.start();
        // give holder time to acquire
        Thread.sleep(50);

        LocalLockManager lm = new LocalLockManager();
        String token = lm.acquire(id, 500, 50, 3);
        assertNull(token, "Acquire should fail when underlying lock is held and attempts exhausted");
        holder.join();
    }

    @Test
    void acquireEventuallySucceedsAfterRelease() throws InterruptedException {
        UUID id = UUID.randomUUID();
        // Holder thread locks then releases after a short delay
        Thread holder = new Thread(() -> {
            ReentrantLock l = TransferLockManager.getLock(id);
            l.lock();
            try {
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            } finally {
                l.unlock();
            }
        });
        holder.start();
        Thread.sleep(20); // ensure holder acquires before we attempt

        LocalLockManager lm = new LocalLockManager();
        String token = lm.acquire(id, 1000, 50, 50);
        assertNotNull(token, "Acquire should succeed after underlying lock is released");
        assertTrue(lm.release(id, token));
        holder.join();
    }
}
