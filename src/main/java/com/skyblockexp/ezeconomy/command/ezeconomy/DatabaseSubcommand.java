package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Subcommand for /ezeconomy database - shows database information
 */
public class DatabaseSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public DatabaseSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ezeconomy.database")) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "storage_unavailable");
            return true;
        }

        Object storageCfg = plugin.getConfig().get("storage");
        String storageType;
        if (storageCfg instanceof String) {
            storageType = ((String) storageCfg).toUpperCase();
        } else {
            storageType = plugin.getConfig().getString("storage.type", "yml").toUpperCase();
        }
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&6=== Database Information ==="));
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&eStorage Type: &f" + storageType));
        boolean connected = false;
        String statusColor = "&cDisconnected";
        try {
            connected = storage.isConnected();
            statusColor = connected ? "&aConnected" : "&cDisconnected";
        } catch (Exception ex) {
            statusColor = "&cError";
        }
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&eConnection Status: " + statusColor));
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&eAvailable Subcommands:"));
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&f  /ezeconomy database test &7- Test database functions"));
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&f  /ezeconomy database reset &7- Reset database tables"));
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&f  /ezeconomy spool size &7- Show local MySQL fallback spool rows"));
        sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, "&f  /ezeconomy spool replay &7- Force replay of local fallback rows"));

        return true;
    }
}