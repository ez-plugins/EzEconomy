package com.skyblockexp.ezeconomy.redis.messaging;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.messaging.MessagingService;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Redis pub/sub transport for cross-server messaging. This transport does not
 * require a proxy plugin; Redis acts as the message broker directly.
 */
public class RedisMessagingTransport implements AutoCloseable {
    private static final String DEFAULT_CHANNEL = "ezeconomy:messages";

    private final RedisClient publishClient;
    private final StatefulRedisConnection<String, String> publishConnection;
    private final RedisCommands<String, String> publishCommands;

    private final RedisClient subscribeClient;
    private final StatefulRedisPubSubConnection<String, String> subscribeConnection;

    private final String channel;
    private final String serverId;
    private final EzEconomyPlugin plugin;
    private final Logger logger;

    public RedisMessagingTransport(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        String host = "127.0.0.1";
        int port = 6379;
        String password = "";
        int database = 0;
        String configuredChannel = DEFAULT_CHANNEL;

        File redisFile = new File(plugin.getDataFolder(), "redis.yml");
        if (redisFile.exists()) {
            FileConfiguration redisCfg = YamlConfiguration.loadConfiguration(redisFile);
            host = redisCfg.getString("host", host);
            port = redisCfg.getInt("port", port);
            password = redisCfg.getString("password", password);
            database = redisCfg.getInt("database", database);
            configuredChannel = redisCfg.getString("messaging.channel", DEFAULT_CHANNEL);
        }

        this.channel = configuredChannel;
        this.serverId = UUID.randomUUID().toString().substring(0, 8);

        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port).withDatabase(database);
        if (password != null && !password.isEmpty()) {
            builder.withPassword(password.toCharArray());
        }
        RedisURI uri = builder.build();

        this.publishClient = RedisClient.create(uri);
        this.publishConnection = publishClient.connect();
        this.publishCommands = publishConnection.sync();

        this.subscribeClient = RedisClient.create(uri);
        this.subscribeConnection = subscribeClient.connectPubSub();
        this.subscribeConnection.addListener(new MessageListener());
        this.subscribeConnection.sync().subscribe(channel);

        logger.info("Redis messaging transport connected (channel: " + channel + ", serverId: " + serverId + ")");
    }

    public void sendNotification(UUID recipientUuid, String recipientName,
                                  String senderName, String amount, String currency) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("NOTIFY");
            out.writeUTF(serverId);
            out.writeUTF(recipientUuid.toString());
            out.writeUTF(recipientName);
            out.writeUTF(senderName);
            out.writeUTF(amount);
            out.writeUTF(currency);
            String payload = Base64.getEncoder().encodeToString(bos.toByteArray());
            boolean primaryThread = false;
            try { primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread(); } catch (Throwable ignored) {}
            if (primaryThread) {
                try {
                    RedisAsyncCommands<String, String> async = publishConnection.async();
                    async.publish(channel, payload);
                } catch (Throwable t) {
                    logger.warning("Failed to publish notification via Redis (async): " + t.getMessage());
                }
            } else {
                publishCommands.publish(channel, payload);
            }
        } catch (IOException e) {
            logger.warning("Failed to publish notification via Redis: " + e.getMessage());
        }
    }

    public void broadcastPlayerList() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("PLAYER_LIST");
            out.writeUTF(serverId);

            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            out.writeInt(online.size());
            for (Player p : online) {
                out.writeUTF(p.getUniqueId().toString());
                out.writeUTF(p.getName());
            }
            String payload = Base64.getEncoder().encodeToString(bos.toByteArray());
            boolean primaryThread = false;
            try { primaryThread = Bukkit.getServer() != null && Bukkit.isPrimaryThread(); } catch (Throwable ignored) {}
            if (primaryThread) {
                try {
                    RedisAsyncCommands<String, String> async = publishConnection.async();
                    async.publish(channel, payload);
                } catch (Throwable t) {
                    logger.warning("Failed to publish player list via Redis (async): " + t.getMessage());
                }
            } else {
                publishCommands.publish(channel, payload);
            }
        } catch (IOException e) {
            logger.warning("Failed to broadcast player list via Redis: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try { subscribeConnection.close(); } catch (Exception ignored) {}
        try { subscribeClient.shutdown(); } catch (Exception ignored) {}
        try { publishConnection.close(); } catch (Exception ignored) {}
        try { publishClient.shutdown(); } catch (Exception ignored) {}
    }

    private class MessageListener extends RedisPubSubAdapter<String, String> {
        @Override
        public void message(String ch, String message) {
            if (!channel.equals(ch)) return;

            try {
                byte[] data = Base64.getDecoder().decode(message);
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                String type = in.readUTF();
                String originServerId = in.readUTF();

                if (serverId.equals(originServerId)) return;

                MessagingService messagingService = plugin.getMessagingService();

                if ("NOTIFY".equals(type)) {
                    String recipientUuidStr = in.readUTF();
                    String recipientName = in.readUTF();
                    String senderName = in.readUTF();
                    String amount = in.readUTF();
                    String currency = in.readUTF();

                    // Schedule delivery on the main server thread to avoid
                    // calling Bukkit API from the Lettuce listener thread.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            Player recipient = Bukkit.getPlayer(UUID.fromString(recipientUuidStr));
                            if (recipient != null && recipient.isOnline()) {
                                String msg = plugin.getMessageProvider().get("received",
                                        Map.of("player", senderName, "amount", amount));
                                recipient.sendMessage(msg);
                            }
                        } catch (Throwable t) {
                            logger.warning("Failed to deliver Redis notify on main thread: " + t.getMessage());
                        }
                    });
                } else if ("PLAYER_LIST".equals(type) && messagingService != null) {
                    int count = in.readInt();
                    Set<String> names = ConcurrentHashMap.newKeySet();
                    Map<String, UUID> uuids = new ConcurrentHashMap<>();
                    for (int i = 0; i < count; i++) {
                        String uuidStr = in.readUTF();
                        String name = in.readUTF();
                        names.add(name);
                        try {
                            uuids.put(name.toLowerCase(Locale.ROOT), UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to process Redis message: " + e.getMessage());
            }
        }
    }
}
