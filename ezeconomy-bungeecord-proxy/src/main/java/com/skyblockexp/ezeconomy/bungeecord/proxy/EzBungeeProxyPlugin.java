package com.skyblockexp.ezeconomy.bungeecord.proxy;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EzBungeeProxyPlugin extends Plugin implements Listener {
    private static final String LOCK_CHANNEL = "ezeconomy:locks";
    private static final String NOTIFY_CHANNEL = "ezeconomy:notify";

    private EzBungeeProxy proxyLogic;
    private final Map<String, UUID> networkPlayerUuids = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        File cfg = new File(getDataFolder(), "bungeecord.yml");
        this.proxyLogic = BungeeAdapterPlugin.loadProxyFromConfig(cfg);

        getProxy().registerChannel(LOCK_CHANNEL);
        getProxy().registerChannel(NOTIFY_CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);

        getProxy().getScheduler().schedule(this, this::broadcastPlayerList, 3, 3, TimeUnit.SECONDS);

        try {
            org.bstats.bungeecord.Metrics metrics = new org.bstats.bungeecord.Metrics(this, 30431);
        } catch (Throwable ignored) {}

        getLogger().info("EzEconomy BungeeCord proxy enabled — registered channels " + LOCK_CHANNEL + " and " + NOTIFY_CHANNEL);
    }

    @Override
    public void onDisable() {
        try { if (proxyLogic != null) proxyLogic.close(); } catch (Exception ignored) {}
        getProxy().unregisterChannel(LOCK_CHANNEL);
        getProxy().unregisterChannel(NOTIFY_CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        String tag = event.getTag();

        if (LOCK_CHANNEL.equals(tag)) {
            handleLockMessage(event);
        } else if (NOTIFY_CHANNEL.equals(tag)) {
            handleNotifyMessage(event);
        }
    }

    private void handleLockMessage(PluginMessageEvent event) {
        if (!(event.getSender() instanceof Server sender)) return;
        event.setCancelled(true);

        byte[] response = proxyLogic.processIncoming(event.getData());
        if (response != null) {
            sender.getInfo().sendData(LOCK_CHANNEL, response);
        }
    }

    private void handleNotifyMessage(PluginMessageEvent event) {
        if (!(event.getSender() instanceof Server sender)) return;
        event.setCancelled(true);

        byte[] data = event.getData();
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String type = in.readUTF();

            if ("NOTIFY".equals(type)) {
                String recipientUuidStr = in.readUTF();
                String recipientName = in.readUTF();
                String senderName = in.readUTF();
                String amount = in.readUTF();
                String currency = in.readUTF();

                UUID recipientUuid = UUID.fromString(recipientUuidStr);
                net.md_5.bungee.api.connection.ProxiedPlayer recipient = getProxy().getPlayer(recipientUuid);

                if (recipient != null && recipient.getServer() != null) {
                    recipient.getServer().getInfo().sendData(NOTIFY_CHANNEL, data);
                    getLogger().info("Forwarded payment notification from " + senderName + " to " + recipientName
                            + " (on " + recipient.getServer().getInfo().getName() + ")");
                } else {
                    sendOfflineResponse(sender.getInfo(), recipientUuidStr, senderName, amount, currency);
                    getLogger().info("Recipient " + recipientName + " not online, sent RECIPIENT_OFFLINE to "
                            + sender.getInfo().getName());
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to process notify message: " + e.getMessage());
        }
    }

    private void sendOfflineResponse(ServerInfo target, String recipientUuid,
                                      String senderName, String amount, String currency) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("RECIPIENT_OFFLINE");
            out.writeUTF(recipientUuid);
            out.writeUTF(senderName);
            out.writeUTF(amount);
            out.writeUTF(currency);
            target.sendData(NOTIFY_CHANNEL, bos.toByteArray());
        } catch (IOException e) {
            getLogger().warning("Failed to send offline response: " + e.getMessage());
        }
    }

    private void broadcastPlayerList() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF("PLAYER_LIST");

            Collection<net.md_5.bungee.api.connection.ProxiedPlayer> allPlayers = getProxy().getPlayers();
            out.writeInt(allPlayers.size());
            for (net.md_5.bungee.api.connection.ProxiedPlayer p : allPlayers) {
                out.writeUTF(p.getUniqueId().toString());
                out.writeUTF(p.getName());
            }
            byte[] data = bos.toByteArray();

            for (ServerInfo server : getProxy().getServers().values()) {
                if (!server.getPlayers().isEmpty()) {
                    server.sendData(NOTIFY_CHANNEL, data);
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to broadcast player list: " + e.getMessage());
        }
    }

    public EzBungeeProxy getProxyLogic() { return proxyLogic; }
}
