package com.skyblockexp.ezeconomy.storage;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class TransferLockManager {
    private static final ConcurrentMap<UUID, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private TransferLockManager() {
    }

    public static ReentrantLock getLock(UUID uuid) {
        return LOCKS.computeIfAbsent(uuid, ignored -> new ReentrantLock());
    }
}
