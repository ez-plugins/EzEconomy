package com.skyblockexp.ezeconomy.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class TransferLockManagerTest {

    @Test
    void sameUuidReturnsSameLock() {
        UUID id = UUID.randomUUID();
        ReentrantLock l1 = TransferLockManager.getLock(id);
        ReentrantLock l2 = TransferLockManager.getLock(id);
        assertSame(l1, l2);
    }

    @Test
    void differentUuidsReturnDifferentLocks() {
        ReentrantLock a = TransferLockManager.getLock(UUID.randomUUID());
        ReentrantLock b = TransferLockManager.getLock(UUID.randomUUID());
        assertNotSame(a, b);
        assertTrue(a instanceof ReentrantLock);
        assertTrue(b instanceof ReentrantLock);
    }
}
