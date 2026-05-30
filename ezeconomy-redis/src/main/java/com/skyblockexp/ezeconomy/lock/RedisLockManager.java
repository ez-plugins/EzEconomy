package com.skyblockexp.ezeconomy.lock;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;

public class RedisLockManager implements LockManager, AutoCloseable {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final String prefix = "ezeconomy:lock:";

    public RedisLockManager(String host, int port, String password, int database) {
        RedisURI.Builder b = RedisURI.builder().withHost(host).withPort(port).withDatabase(database);
        if (password != null && !password.isEmpty()) b.withPassword(password.toCharArray());
        RedisURI uri = b.build();
        this.client = RedisClient.create(uri);
        this.connection = client.connect();
        this.commands = connection.sync();
    }

    @Override
    public String acquire(UUID uuid, long ttlMs, long retryMs, int maxAttempts) throws InterruptedException {
        String key = prefix + uuid.toString();
        String token = UUID.randomUUID().toString();
        SetArgs args = SetArgs.Builder.nx().px(ttlMs);
        boolean primaryThread = false;
        try {
            primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread();
        } catch (Throwable ignored) {
            primaryThread = false;
        }

        // If running on the primary server thread, do a single non-blocking attempt
        // to avoid hanging the main thread; callers can fall back to local sync
        // paths if null is returned (see LocalLockManager behavior).
        if (primaryThread) {
            try {
                String res = commands.set(key, token, args);
                if ("OK".equalsIgnoreCase(res)) return token;
            } catch (Exception ex) {
                // Don't propagate runtime exceptions from Redis to the main thread;
                // treat as a miss so callers may fallback to local locking.
                return null;
            }
            return null;
        }

        long waitMs = Math.max(0L, retryMs);
        int attempts = 0;
        while (maxAttempts <= 0 || attempts < maxAttempts) {
            try {
                String res = commands.set(key, token, args);
                if ("OK".equalsIgnoreCase(res)) return token;
            } catch (Exception ex) {
                throw new RuntimeException("Redis error while acquiring lock: " + ex.getMessage(), ex);
            }
            attempts++;
            if (waitMs > 0) TimeUnit.MILLISECONDS.sleep(waitMs);
        }
        return null;
    }

    @Override
    public boolean release(UUID uuid, String token) {
        String key = prefix + uuid.toString();
        String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
        boolean primaryThread = false;
        try {
            primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread();
        } catch (Throwable ignored) {
            primaryThread = false;
        }

        // Avoid blocking the Minecraft main thread on network IO. If we're on the
        // primary thread, schedule an async eval and return true to indicate the
        // release was queued. Callers in the codebase generally ignore the return
        // value, so this is a safe best-effort approach to avoid hangs.
        if (primaryThread) {
            try {
                connection.async().eval(script, ScriptOutputType.INTEGER, new String[]{key}, token)
                        .whenComplete((r, ex) -> {
                            if (ex != null) {
                                // swallow; best-effort release
                            }
                        });
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        try {
            Object res = commands.eval(script, ScriptOutputType.INTEGER, new String[]{key}, token);
            if (res instanceof Number) {
                return ((Number) res).longValue() == 1L;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null) connection.close();
        } finally {
            if (client != null) client.shutdown();
        }
    }
}
