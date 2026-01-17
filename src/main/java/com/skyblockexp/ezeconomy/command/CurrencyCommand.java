package com.skyblockexp.ezeconomy.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

public class CurrencyCommand implements CommandExecutor {
	private final EzEconomyPlugin plugin;

	public CurrencyCommand(EzEconomyPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		MessageProvider messages = plugin.getMessageProvider();
		FileConfiguration config = plugin.getConfig();
		boolean multiEnabled = config.getBoolean("multi-currency.enabled", false);
		if (!multiEnabled) {
			sender.sendMessage(messages.color(messages.get("multi_currency_disabled")));
			return true;
		}

		if (!(sender instanceof Player)) {
			sender.sendMessage(messages.color(messages.get("only_players")));
			return true;
		}
		Player player = (Player) sender;
		CurrencyPreferenceManager preferenceManager = plugin.getCurrencyPreferenceManager();

		Map<String, Object> currencies = config.getConfigurationSection("multi-currency.currencies").getValues(false);
		String preferred = preferenceManager.getPreferredCurrency(player.getUniqueId());

		if (args.length == 0) {
			sender.sendMessage(messages.get("preferred_currency", Map.of("currency", preferred)));
			sender.sendMessage(messages.get("available_currencies"));
			for (String currency : currencies.keySet()) {
				sender.sendMessage(" - " + currency);
			}
			sender.sendMessage(messages.get("use_currency"));
			return true;
		}

		String newCurrency = args[0].toLowerCase();
		if (!currencies.containsKey(newCurrency)) {
			sender.sendMessage(messages.get("unknown_currency", Map.of("currency", newCurrency)));
			return true;
		}

		// Set preferred currency (demo: metadata)
		preferenceManager.setPreferredCurrency(player.getUniqueId(), newCurrency);
		sender.sendMessage(messages.get("set_currency", Map.of("currency", newCurrency)));
		return true;
	}
}
