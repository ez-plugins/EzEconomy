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
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.database")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            sender.sendMessage(messages.color(messages.get("storage_unavailable")));
            return true;
        }

        String storageType = plugin.getConfig().getString("storage.type", "yml").toUpperCase();
        sender.sendMessage(messages.color("&6=== Database Information ==="));
        sender.sendMessage(messages.color("&eStorage Type: &f" + storageType));
        boolean connected = false;
        String statusColor = "&cDisconnected";
        try {
            connected = storage.isConnected();
            statusColor = connected ? "&aConnected" : "&cDisconnected";
        } catch (Exception ex) {
            statusColor = "&cError";
        }
        sender.sendMessage(messages.color("&eConnection Status: " + statusColor));
        sender.sendMessage(messages.color("&eAvailable Subcommands:"));
        sender.sendMessage(messages.color("&f  /ezeconomy database test &7- Test database functions"));
        sender.sendMessage(messages.color("&f  /ezeconomy database reset &7- Reset database tables"));

        return true;
    }
}