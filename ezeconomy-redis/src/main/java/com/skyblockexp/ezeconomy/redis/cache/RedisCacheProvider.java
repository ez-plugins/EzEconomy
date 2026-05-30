package com.skyblockexp.ezeconomy.redis.cache;

import com.skyblockexp.ezeconomy.cache.CacheProvider;
import com.skyblockexp.ezeconomy.cache.ExpiringCache;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.bukkit.Bukkit;

/**
 * Redis-backed cache provider using Lettuce. Stores string values.
 */
public class RedisCacheProvider<K, V> implements CacheProvider<K, V>, AutoCloseable {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final boolean fallbackToLocal;

    public RedisCacheProvider() {
        // Attempt to read redis.yml from plugin data folder; fall back to localhost
        String host = "127.0.0.1";
        int port = 6379;
        int database = 0;
        boolean fallback = true;
        try {
            EzEconomyPlugin plugin = EzEconomyPlugin.getInstance();
            if (plugin != null) {
                File redisFile = new File(plugin.getDataFolder(), "redis.yml");
                if (!redisFile.exists()) {
                    try { plugin.saveResource("redis.yml", false); } catch (Exception ignored) {}
                }
                FileConfiguration redisCfg = YamlConfiguration.loadConfiguration(redisFile);
                host = redisCfg.getString("host", host);
                port = redisCfg.getInt("port", port);
                database = redisCfg.getInt("database", database);
                fallback = redisCfg.getBoolean("fallback-to-local", true);
            }
        } catch (Throwable ignored) {}
        RedisURI uri = RedisURI.builder().withHost(host).withPort(port).withDatabase(database).build();
        this.client = RedisClient.create(uri);
        this.connection = client.connect();
        this.commands = connection.sync();
        this.fallbackToLocal = fallback;
    }

    private String keyToStr(Object k) { return "ezeconomy:cache:" + String.valueOf(k); }

    @Override
    public ExpiringCache.Entry<V> getEntry(K key) {
        String k = keyToStr(key);
        boolean primaryThread = false;
        try { primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread(); } catch (Throwable ignored) {}
        if (primaryThread && fallbackToLocal) return null;
        try {
            String v = commands.get(k);
            if (v == null) return null;
            // Lettuce does not return expiry in sync API easily; return non-expiring sentinel
            return new ExpiringCache.Entry<>((V) v, Long.MAX_VALUE);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void put(K key, V value, long ttlMs) {
        String k = keyToStr(key);
        boolean primaryThread = false;
        try { primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread(); } catch (Throwable ignored) {}
        if (primaryThread && fallbackToLocal) {
            // Fire-and-forget via async API to avoid blocking the main thread
            try {
                RedisAsyncCommands<String, String> async = connection.async();
                if (ttlMs > 0) async.setex(k, Math.max(1, ttlMs / 1000), String.valueOf(value));
                else async.set(k, String.valueOf(value));
            } catch (Throwable ignored) {}
            return;
        }
        if (ttlMs > 0) commands.setex(k, Math.max(1, ttlMs / 1000), String.valueOf(value));
        else commands.set(k, String.valueOf(value));
    }

    @Override
    public void remove(K key) {
        String k = keyToStr(key);
        boolean primaryThread = false;
        try { primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread(); } catch (Throwable ignored) {}
        if (primaryThread && fallbackToLocal) {
            try { connection.async().del(k); } catch (Throwable ignored) {}
            return;
        }
        commands.del(k);
    }

    @Override
    public void close() throws Exception {
        try { connection.close(); } catch (Exception ignored) {}
        try { client.shutdown(); } catch (Exception ignored) {}
    }
}
