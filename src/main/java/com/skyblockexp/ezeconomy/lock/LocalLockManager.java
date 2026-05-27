package com.skyblockexp.ezeconomy.lock;

import org.bukkit.Bukkit;

import com.skyblockexp.ezeconomy.storage.TransferLockManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LocalLockManager implements LockManager {
    private final Map<String, UUID> tokenOwners = new ConcurrentHashMap<>();

    @Override
    public String acquire(UUID uuid, long ttlMs, long retryMs, int maxAttempts) throws InterruptedException {
        ReentrantLock lock = TransferLockManager.getLock(uuid);
        // Never block the primary thread in a sleep-retry loop:
        // callers can fall back to local synchronization paths if needed.
        boolean primaryThread = false;
        try {
            primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread();
        } catch (Throwable ignored) {
            primaryThread = false;
        }
        if (primaryThread) {
            if (!lock.tryLock()) return null;
            String token = "local-" + UUID.randomUUID();
            tokenOwners.put(token, uuid);
            return token;
        }

        long waitMs = Math.max(0L, retryMs);
        int attempts = 0;
        while (maxAttempts <= 0 || attempts < maxAttempts) {
            boolean acquired = waitMs <= 0 ? lock.tryLock() : lock.tryLock(waitMs, TimeUnit.MILLISECONDS);
            if (acquired) {
                String token = "local-" + UUID.randomUUID();
                tokenOwners.put(token, uuid);
                return token;
            }
            attempts++;
        }
        return null;
    }

    @Override
    public boolean release(UUID uuid, String token) {
        if (token == null) return false;
        UUID owner = tokenOwners.remove(token);
        if (owner == null || !owner.equals(uuid)) return false;
        ReentrantLock lock = TransferLockManager.getLock(uuid);
        try {
            lock.unlock();
            return true;
        } catch (IllegalMonitorStateException ex) {
            return false;
        }
    }
}
