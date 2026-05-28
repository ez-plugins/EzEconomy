package com.skyblockexp.ezeconomy.messaging;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessagingService implements PluginMessageListener {
	public static final String CHANNEL = "ezeconomy:notify";
	private final EzEconomyPlugin plugin;
	private final Set<String> networkPlayers = ConcurrentHashMap.newKeySet();
	private final Map<String, UUID> networkPlayerUuids = new ConcurrentHashMap<>();
	private final Map<UUID, List<String>> localPendingNotifications = new ConcurrentHashMap<>();

	public MessagingService(EzEconomyPlugin plugin) {
		this.plugin = plugin;
	}

	public void register() {
		Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
		Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
		logVerbose("Registered cross-server messaging channel: " + CHANNEL);
	}

	public void unregister() {
		Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
		Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
	}

	public Set<String> getNetworkPlayers() {
		return Collections.unmodifiableSet(networkPlayers);
	}

	public boolean isNetworkPlayer(String name) {
		return name != null && networkPlayerUuids.containsKey(name.toLowerCase(Locale.ROOT));
	}

	public UUID getNetworkPlayerUuid(String name) {
		if (name == null) return null;
		return networkPlayerUuids.get(name.toLowerCase(Locale.ROOT));
	}

	public void sendPaymentNotification(UUID recipientUuid, String recipientName,
										String senderName, String amount, String currency) {
		Player relay = findRelayPlayer();
		if (relay == null) {
			storePendingNotification(recipientUuid, recipientName, senderName, amount, currency);
			return;
		}

		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeUTF("NOTIFY");
			out.writeUTF(recipientUuid.toString());
			out.writeUTF(recipientName);
			out.writeUTF(senderName);
			out.writeUTF(amount);
			out.writeUTF(currency);
			relay.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
			logVerbose("Relayed cross-server notification for recipient=" + recipientName + " via " + relay.getName());
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to send cross-server notification: " + e.getMessage());
			storePendingNotification(recipientUuid, recipientName, senderName, amount, currency);
		}
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] data) {
		if (!CHANNEL.equals(channel)) return;

		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			String type = in.readUTF();

			if ("NOTIFY".equals(type)) {
				handleNotify(in);
			} else if ("RECIPIENT_OFFLINE".equals(type)) {
				handleRecipientOffline(in);
			} else if ("PLAYER_LIST".equals(type)) {
				handlePlayerList(in);
			}
		} catch (Exception e) {
			plugin.getLogger().warning("Failed to read cross-server message: " + e.getMessage());
		}
	}

	private void handleNotify(DataInputStream in) throws IOException {
		String recipientUuidStr = in.readUTF();
		String recipientName = in.readUTF();
		String senderName = in.readUTF();
		String amount = in.readUTF();
		String currency = in.readUTF();
		logVerbose("Received cross-server NOTIFY: recipient=" + recipientName
				+ " uuid=" + recipientUuidStr + " from=" + senderName + " amount=" + amount);

		Player recipient = Bukkit.getPlayer(UUID.fromString(recipientUuidStr));
		if (recipient != null && recipient.isOnline()) {
			String msg = plugin.getMessageProvider().get("received",
					Map.of("player", senderName, "amount", amount));
			logVerbose("Delivering cross-server message to " + recipientName + ": " + msg);
			recipient.sendMessage(msg);
		} else {
			logVerbose("Cross-server NOTIFY: recipient " + recipientName + " not found locally");
		}
	}

	private void handleRecipientOffline(DataInputStream in) throws IOException {
		String recipientUuidStr = in.readUTF();
		String senderName = in.readUTF();
		String amount = in.readUTF();
		String currency = in.readUTF();
		storePendingNotification(UUID.fromString(recipientUuidStr), recipientUuidStr, senderName, amount, currency);
	}

	private void handlePlayerList(DataInputStream in) throws IOException {
		int count = in.readInt();
		Set<String> newList = ConcurrentHashMap.newKeySet();
		Map<String, UUID> newUuidMap = new ConcurrentHashMap<>();
		for (int i = 0; i < count; i++) {
			String first = in.readUTF();
			UUID uuid = null;
			String name;
			try {
				uuid = UUID.fromString(first);
				name = in.readUTF();
			} catch (IllegalArgumentException ignored) {
				name = first;
			}
			newList.add(name);
			if (uuid == null) {
				Player local = Bukkit.getPlayerExact(name);
				if (local != null) {
					uuid = local.getUniqueId();
				} else {
					StorageProvider storage = plugin.getStorageOrWarn();
					if (storage != null) {
						uuid = storage.resolvePlayerByName(name);
					}
				}
			}
			if (uuid != null) {
				newUuidMap.put(name.toLowerCase(Locale.ROOT), uuid);
			}
		}
		networkPlayers.clear();
		networkPlayers.addAll(newList);
		networkPlayerUuids.clear();
		networkPlayerUuids.putAll(newUuidMap);
	}

	public void deliverPendingNotifications(Player player) {
		StorageProvider storage = plugin.getStorageOrWarn();
		List<String> messages = new ArrayList<>();
		if (storage != null) {
			messages.addAll(storage.pollPendingNotifications(player.getUniqueId()));
		}
		List<String> drained = localPendingNotifications.remove(player.getUniqueId());
		if (drained != null && !drained.isEmpty()) {
			messages.addAll(drained);
			logVerbose("Delivered " + drained.size() + " in-memory pending notifications to " + player.getName());
		}
		for (String msg : messages) {
			player.sendMessage(msg);
		}
	}

	private void storePendingNotification(UUID recipientUuid, String recipientName, String senderName, String amount, String currency) {
		StorageProvider storage = plugin.getStorageOrWarn();
		String msg = plugin.getMessageProvider().get("received",
				Map.of("player", senderName, "amount", amount));
		if (storage != null) {
			storage.insertPendingNotification(recipientUuid, msg);
			return;
		}
		localPendingNotifications.computeIfAbsent(recipientUuid, ignored -> Collections.synchronizedList(new ArrayList<>())).add(msg);
		logVerbose("Queued in-memory pending notification for recipient=" + recipientName);
	}

	private Player findRelayPlayer() {
		Collection<? extends Player> online = Bukkit.getOnlinePlayers();
		return online.isEmpty() ? null : online.iterator().next();
	}

	private void logVerbose(String message) {
		if (plugin.isDebugEnabled()) {
			plugin.getLogger().info(message);
		}
	}
}
