
package com.skyblockexp.ezeconomy.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.io.File;


/**
 * Handles the /ezeconomy admin command and its subcommands.
 * Includes daily reward reset and orphaned player data cleanup for all supported storage types.
 *
 * This class is part of the open-source EzEconomy project.
 */
public class EzEconomyCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;
    private final DailyRewardManager dailyRewardManager;

    public EzEconomyCommand(EzEconomyPlugin plugin, DailyRewardManager dailyRewardManager) {
        this.plugin = plugin;
        this.dailyRewardManager = dailyRewardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (args.length == 0) {
            sender.sendMessage(messages.color(messages.get("usage_ezeconomy")));
            return true;
        }

        // Handle /ezeconomy cleanup [confirm]: Remove orphaned player data from storage (all types)
        if (args[0].equalsIgnoreCase("cleanup")) {
            if (!sender.hasPermission("ezeconomy.admin.cleanup")) {
                sender.sendMessage(messages.color(messages.get("no_permission")));
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                sender.sendMessage(messages.color("&eThis will remove orphaned UUIDs (player files with no known player) from storage. Type /ezeconomy cleanup confirm to proceed."));
                return true;
            }
            Object storage = plugin.getStorageOrWarn();
            java.util.Set<String> orphaned = new java.util.HashSet<>();
            // Preview orphaned entries/files
            if (storage instanceof com.skyblockexp.ezeconomy.storage.YMLStorageProvider) {
                orphaned = ((com.skyblockexp.ezeconomy.storage.YMLStorageProvider) storage).previewOrphanedPlayers();
            } else if (storage instanceof com.skyblockexp.ezeconomy.storage.MySQLStorageProvider) {
                orphaned = ((com.skyblockexp.ezeconomy.storage.MySQLStorageProvider) storage).previewOrphanedPlayers();
            } else if (storage instanceof com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider) {
                orphaned = ((com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider) storage).previewOrphanedPlayers();
            } else if (storage instanceof com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider) {
                orphaned = ((com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider) storage).previewOrphanedPlayers();
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                if (orphaned.isEmpty()) {
                    sender.sendMessage(messages.color("&aNo orphaned player entries found. Nothing will be deleted."));
                } else {
                    sender.sendMessage(messages.color("&eThe following orphaned player entries will be deleted if you confirm: " + String.join(", ", orphaned)));
                    sender.sendMessage(messages.color("&eType /ezeconomy cleanup confirm to proceed."));
                }
                return true;
            }
            // Actual cleanup
            java.util.Set<String> removed = new java.util.HashSet<>();
            if (storage instanceof com.skyblockexp.ezeconomy.storage.YMLStorageProvider) {
                com.skyblockexp.ezeconomy.storage.YMLStorageProvider yml = (com.skyblockexp.ezeconomy.storage.YMLStorageProvider) storage;
                File dataFolder = plugin.getDataFolder();
                File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        String fname = file.getName();
                        if (orphaned.contains(fname)) {
                            if (file.delete()) {
                                removed.add(fname);
                            }
                        }
                    }
                }
            } else if (storage instanceof com.skyblockexp.ezeconomy.storage.MySQLStorageProvider) {
                removed = ((com.skyblockexp.ezeconomy.storage.MySQLStorageProvider) storage).cleanupOrphanedPlayers();
            } else if (storage instanceof com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider) {
                removed = ((com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider) storage).cleanupOrphanedPlayers();
            } else if (storage instanceof com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider) {
                removed = ((com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider) storage).cleanupOrphanedPlayers();
            }
            if (removed.isEmpty()) {
                sender.sendMessage(messages.color("&aNo orphaned player entries found."));
            } else {
                sender.sendMessage(messages.color("&aRemoved orphaned player entries: " + String.join(", ", removed)));
            }
            return true;
        }
        // Handle /ezeconomy daily reset <player>: Reset daily reward cooldown for a player
        if (args.length >= 2 && args[0].equalsIgnoreCase("daily")
                && args[1].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("ezeconomy.admin.daily")) {
                sender.sendMessage(messages.color(messages.get("no_permission")));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(messages.color(messages.get("usage_daily_reset")));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            dailyRewardManager.resetReward(target.getUniqueId());
            sender.sendMessage(messages.color(messages.get("daily_reset", java.util.Map.of(
                    "player", target.getName() == null ? args[2] : target.getName()
            ))));
            return true;
        }
        // Unknown subcommand
        sender.sendMessage(messages.color(messages.get("unknown_subcommand")));
        return true;
    }
}
