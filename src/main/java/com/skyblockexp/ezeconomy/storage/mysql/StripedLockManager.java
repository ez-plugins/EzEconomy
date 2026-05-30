package com.skyblockexp.ezeconomy.storage.mysql;

import java.util.UUID;

/**
 * Small utility providing a fixed-size stripe of lock objects to reduce
 * contention for per-key synchronization without unbounded memory growth.
 */
public class StripedLockManager {
    private final Object[] stripes;

    public StripedLockManager(int stripesCount) {
        int s = Math.max(1, stripesCount);
        this.stripes = new Object[s];
        for (int i = 0; i < s; i++) this.stripes[i] = new Object();
    }

    public Object lockFor(String key) {
        if (key == null) return stripes[0];
        return stripes[(key.hashCode() & 0x7fffffff) % stripes.length];
    }

    public Object lockFor(UUID uuid) {
        return lockFor(uuid == null ? null : uuid.toString());
    }
}
