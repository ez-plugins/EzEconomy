package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.command.CommandSender;

/**
 * Subcommand for /ezeconomy database reset - resets database and rebuilds tables
 */
public class DatabaseResetSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public DatabaseResetSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.database.reset")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(messages.color("&cThis command will reset the entire database and rebuild all tables."));
            sender.sendMessage(messages.color("&cALL DATA WILL BE LOST! Use &f/ezeconomy database reset confirm &cto proceed."));
            return true;
        }

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            sender.sendMessage(messages.color(messages.get("storage_unavailable")));
            return true;
        }

        sender.sendMessage(messages.color("&6Resetting database..."));

        try {
            // Shutdown current storage
            storage.shutdown();

            // Reinitialize storage (this should recreate tables)
            storage.init();

            sender.sendMessage(messages.color("&aDatabase reset and rebuild complete."));

        } catch (Exception e) {
            sender.sendMessage(messages.color("&cDatabase reset failed: " + e.getMessage()));
            return true;
        }

        return true;
    }
}