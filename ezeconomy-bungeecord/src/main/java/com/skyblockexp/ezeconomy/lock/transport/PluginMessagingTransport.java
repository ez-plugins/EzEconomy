package com.skyblockexp.ezeconomy.lock.transport;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Plugin messaging transport using Bukkit plugin messaging channel to communicate with proxy.
 * This requires a player to be online to send messages to the proxy; the implementation will
 * pick an arbitrary online player when sending. Responses are expected on the same channel.
 */
public class PluginMessagingTransport implements LockTransport, PluginMessageListener {
    private final Plugin plugin;
    private final String channel;
    private final long responseTimeoutMs;
    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<CacheResponse>> pendingCache = new ConcurrentHashMap<>();
    private final String sharedSecret;

    public PluginMessagingTransport(Plugin plugin, String channel, long responseTimeoutMs) {
        this(plugin, channel, responseTimeoutMs, "");
    }

    public PluginMessagingTransport(Plugin plugin, String channel, long responseTimeoutMs, String sharedSecret) {
        this.plugin = plugin;
        this.channel = channel;
        this.responseTimeoutMs = responseTimeoutMs;
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, this);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to register plugin channels: " + ex.getMessage());
        }
    }

    @Override
    public String acquire(UUID uuid, long ttlMs) throws InterruptedException {
        String requestId = Long.toHexString(System.nanoTime()) + java.util.UUID.randomUUID().toString();
        CompletableFuture<String> fut = new CompletableFuture<>();
        pending.put(requestId, fut);
        try {
            try {
                sendMessage(buildAcquirePayload(requestId, uuid, ttlMs));
            } catch (Exception e) {
                // sending failed
                pending.remove(requestId);
                return null;
            }
            try {
                return fut.get(responseTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                return null;
            }
        } finally {
            pending.remove(requestId);
        }
    }

    @Override
    public boolean release(UUID uuid, String token) {
        String requestId = Long.toHexString(System.nanoTime()) + java.util.UUID.randomUUID().toString();
        // fire-and-forget release
        try { sendMessage(buildReleasePayload(requestId, uuid, token)); } catch (Exception ignored) {}
        return true;
    }

    private byte[] buildAcquirePayload(String requestId, UUID uuid, long ttlMs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF("ACQUIRE");
        out.writeUTF(sharedSecret == null ? "" : sharedSecret);
        out.writeUTF(requestId);
        out.writeUTF(uuid.toString());
        out.writeLong(ttlMs);
        out.flush();
        return baos.toByteArray();
    }

    private byte[] buildReleasePayload(String requestId, UUID uuid, String token) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF("RELEASE");
        out.writeUTF(sharedSecret == null ? "" : sharedSecret);
        out.writeUTF(requestId);
        out.writeUTF(uuid.toString());
        out.writeUTF(token == null ? "" : token);
        out.flush();
        return baos.toByteArray();
    }

    private void sendMessage(byte[] payload) throws Exception {
        Player player = null;
        if (!Bukkit.getOnlinePlayers().isEmpty()) player = Bukkit.getOnlinePlayers().iterator().next();
        if (player == null) throw new IllegalStateException("No online players available to send plugin message");
        player.sendPluginMessage(plugin, channel, payload);
    }

    /** Cache response container */
    public static final class CacheResponse {
        public final String value;
        public final long expiresAt;
        public CacheResponse(String value, long expiresAt) { this.value = value; this.expiresAt = expiresAt; }
    }

    /**
     * Request a cache value from the proxy and wait for a response.
     */
    public String getCache(String key, long timeoutMs) throws InterruptedException {
        String requestId = Long.toHexString(System.nanoTime()) + java.util.UUID.randomUUID().toString();
        CompletableFuture<CacheResponse> fut = new CompletableFuture<>();
        pendingCache.put(requestId, fut);
        try {
            try { sendMessage(buildCacheGetPayload(requestId, key)); } catch (Exception e) { pendingCache.remove(requestId); return null; }
            try {
                CacheResponse resp = fut.get(timeoutMs, TimeUnit.MILLISECONDS);
                return resp == null ? null : resp.value;
            } catch (Exception ex) {
                return null;
            }
        } finally {
            pendingCache.remove(requestId);
        }
    }

    public void setCache(String key, String value, long ttlMs) {
        String requestId = Long.toHexString(System.nanoTime()) + java.util.UUID.randomUUID().toString();
        try { sendMessage(buildCacheSetPayload(requestId, key, value, ttlMs)); } catch (Exception ignored) {}
    }

    private byte[] buildCacheGetPayload(String requestId, String key) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF("CACHE_GET");
        out.writeUTF(sharedSecret == null ? "" : sharedSecret);
        out.writeUTF(requestId);
        out.writeUTF(key == null ? "" : key);
        out.flush();
        return baos.toByteArray();
    }

    private byte[] buildCacheSetPayload(String requestId, String key, String value, long ttlMs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF("CACHE_SET");
        out.writeUTF(sharedSecret == null ? "" : sharedSecret);
        out.writeUTF(requestId);
        out.writeUTF(key == null ? "" : key);
        out.writeUTF(value == null ? "" : value);
        out.writeLong(ttlMs);
        out.flush();
        return baos.toByteArray();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!this.channel.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String action = in.readUTF();
            String incomingSecret = in.readUTF();
            if (this.sharedSecret != null && this.sharedSecret.length() > 0) {
                if (incomingSecret == null || !this.sharedSecret.equals(incomingSecret)) return;
            }
            String requestId = in.readUTF();
            if ("ACQUIRE_RESPONSE".equals(action)) {
                String token = in.readUTF();
                CompletableFuture<String> fut = pending.get(requestId);
                if (fut != null) fut.complete(token.length() == 0 ? null : token);
            } else if ("CACHE_GET_RESPONSE".equals(action)) {
                String value = in.readUTF();
                long expiresAt = in.readLong();
                CompletableFuture<CacheResponse> fut = pendingCache.get(requestId);
                if (fut != null) fut.complete(new CacheResponse(value.length() == 0 ? null : value, expiresAt));
            }
        } catch (Exception ignored) {}
    }
}
