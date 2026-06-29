package com.skyblockexp.ezeconomy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(id = "ezeconomy", name = "EzEconomy", version = "3.1.3",
		description = "Cross-server payment forwarding and global player list for EzEconomy",
		authors = {"Shadow48402"})
public class EzEconomyVelocity {
	private static final MinecraftChannelIdentifier CHANNEL =
			MinecraftChannelIdentifier.create("ezeconomy", "notify");

	private final ProxyServer server;
	private final Logger logger;

	@Inject
	public EzEconomyVelocity(ProxyServer server, Logger logger) {
		this.server = server;
		this.logger = logger;
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent event) {
		server.getChannelRegistrar().register(CHANNEL);
		logger.info("EzEconomy Velocity plugin enabled — registered channel ezeconomy:notify");

		server.getScheduler().buildTask(this, this::broadcastPlayerList)
				.repeat(3, TimeUnit.SECONDS)
				.schedule();
	}

	@Subscribe
	public void onPluginMessage(PluginMessageEvent event) {
		if (!CHANNEL.equals(event.getIdentifier())) return;
		if (!(event.getSource() instanceof ServerConnection source)) return;
		event.setResult(PluginMessageEvent.ForwardResult.handled());

		byte[] data = event.getData();

		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			String type = in.readUTF();

			if ("NOTIFY".equals(type)) {
				handleNotify(in, source, data);
			}
		} catch (IOException e) {
			logger.warn("Failed to process plugin message: {}", e.getMessage());
		}
	}

	private void handleNotify(DataInputStream in, ServerConnection source, byte[] originalData) throws IOException {
		String recipientUuidStr = in.readUTF();
		String recipientName = in.readUTF();
		String senderName = in.readUTF();
		String amount = in.readUTF();
		String currency = in.readUTF();

		UUID recipientUuid = UUID.fromString(recipientUuidStr);
		Optional<Player> recipient = server.getPlayer(recipientUuid);

		if (recipient.isPresent()) {
			Optional<ServerConnection> conn = recipient.get().getCurrentServer();
			if (conn.isPresent()) {
				conn.get().sendPluginMessage(CHANNEL, originalData);
				logger.info("Forwarded payment notification from {} to {} (on {})",
						senderName, recipientName, conn.get().getServerInfo().getName());
			}
		} else {
			sendOfflineResponse(source, recipientUuidStr, senderName, amount, currency);
			logger.info("Recipient {} not online, sent RECIPIENT_OFFLINE to {}",
					recipientName, source.getServerInfo().getName());
		}
	}

	private void sendOfflineResponse(ServerConnection source, String recipientUuid,
									  String senderName, String amount, String currency) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeUTF("RECIPIENT_OFFLINE");
			out.writeUTF(recipientUuid);
			out.writeUTF(senderName);
			out.writeUTF(amount);
			out.writeUTF(currency);
			source.sendPluginMessage(CHANNEL, bos.toByteArray());
		} catch (IOException e) {
			logger.warn("Failed to send offline response: {}", e.getMessage());
		}
	}

	private void broadcastPlayerList() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeUTF("PLAYER_LIST");

			var allPlayers = server.getAllPlayers();
			out.writeInt(allPlayers.size());
			for (Player p : allPlayers) {
				out.writeUTF(p.getUniqueId().toString());
				out.writeUTF(p.getUsername());
			}
			byte[] data = bos.toByteArray();

			for (RegisteredServer rs : server.getAllServers()) {
				if (!rs.getPlayersConnected().isEmpty()) {
					rs.sendPluginMessage(CHANNEL, data);
				}
			}
		} catch (IOException e) {
			logger.warn("Failed to broadcast player list: {}", e.getMessage());
		}
	}
}
