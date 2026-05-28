package com.skyblockexp.ezeconomy.bungeecord.cache;

import com.skyblockexp.ezeconomy.cache.CacheProvider;
import com.skyblockexp.ezeconomy.cache.ExpiringCache;
import com.skyblockexp.ezeconomy.lock.transport.PluginMessagingTransport;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;

/**
 * Bungee cache provider that uses plugin messaging transport to forward cache
 * requests to the proxy-side EzBungeeProxy. Falls back to local cache when
 * messaging is unavailable.
 */
public class BungeeCacheProvider<K, V> implements CacheProvider<K, V> {
    private final PluginMessagingTransport transport;
    private final ExpiringCache<K, V> fallback = new ExpiringCache<>();

    public BungeeCacheProvider() {
        PluginMessagingTransport t = null;
        try {
            EzEconomyPlugin plugin = EzEconomyPlugin.getInstance();
            if (plugin != null) {
                String channel = plugin.getConfig().getString("bungee.channel", "ezeconomy:locks");
                long timeout = plugin.getConfig().getLong("bungee.response-timeout-ms", 5000L);
                t = new PluginMessagingTransport(plugin, channel, timeout);
            }
        } catch (Throwable ignored) {}
        this.transport = t;
    }

    @Override
    public ExpiringCache.Entry<V> getEntry(K key) {
        if (transport == null) return fallback.getEntry(key);
        try {
            String v = transport.getCache(String.valueOf(key), 2000L);
            if (v == null) return null;
            return new ExpiringCache.Entry<>((V) v, Long.MAX_VALUE);
        } catch (Throwable t) {
            return fallback.getEntry(key);
        }
    }

    @Override
    public void put(K key, V value, long ttlMs) {
        if (transport == null) { fallback.put(key, value, ttlMs); return; }
        try { transport.setCache(String.valueOf(key), String.valueOf(value), ttlMs); } catch (Throwable ignored) { fallback.put(key, value, ttlMs); }
    }

    @Override
    public void remove(K key) {
        if (transport == null) { fallback.remove(key); return; }
        try { transport.setCache(String.valueOf(key), "", 0); } catch (Throwable ignored) { fallback.remove(key); }
    }
}
