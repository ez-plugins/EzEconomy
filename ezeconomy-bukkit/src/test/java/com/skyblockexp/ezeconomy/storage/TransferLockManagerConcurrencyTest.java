package com.skyblockexp.ezeconomy.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class TransferLockManagerConcurrencyTest {

    @Test
    void lockBlocksOtherThreads() throws Exception {
        UUID id = UUID.randomUUID();
        ReentrantLock lock = TransferLockManager.getLock(id);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            } finally {
                lock.unlock();
            }
        });

        holder.start();

        // give the holder thread a chance to take the lock
        Thread.sleep(50);

        ReentrantLock same = TransferLockManager.getLock(id);
        assertSame(lock, same);

        // trying to acquire with small timeout should fail while holder holds the lock
        boolean got = same.tryLock(100, TimeUnit.MILLISECONDS);
        assertFalse(got, "tryLock should time out while another thread holds the lock");

        // now wait for holder to finish and try again -- should succeed
        holder.join();
        boolean gotAfter = same.tryLock(500, TimeUnit.MILLISECONDS);
        try {
            assertTrue(gotAfter, "tryLock should succeed after holder releases the lock");
        } finally {
            if (gotAfter) same.unlock();
        }
    }
}
