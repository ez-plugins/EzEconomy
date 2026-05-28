package com.skyblockexp.ezeconomy.bootstrap.component;

import com.skyblockexp.ezeconomy.bootstrap.BootstrapComponent;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.messaging.MessagingService;

public class MessagingComponent implements BootstrapComponent {
	private final EzEconomyPlugin plugin;
	private MessagingService messagingService;

	public MessagingComponent(EzEconomyPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void start() {
		boolean enabled = plugin.getConfig().getBoolean("cross-server.enabled", false);
		if (!enabled) {
			plugin.getLogger().info("Cross-server messaging is disabled.");
			return;
		}
		messagingService = new MessagingService(plugin);
		messagingService.register();
		plugin.setMessagingService(messagingService);
		plugin.getLogger().info("Cross-server messaging enabled.");
	}

	@Override
	public void stop() {
		if (messagingService != null) {
			messagingService.unregister();
			messagingService = null;
		}
	}

	@Override
	public void reload() {
		stop();
		start();
	}
}
