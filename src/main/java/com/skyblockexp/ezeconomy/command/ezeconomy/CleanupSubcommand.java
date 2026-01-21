package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider;
import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider;
import com.skyblockexp.ezeconomy.storage.YMLStorageProvider;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Handles the /ezeconomy cleanup subcommand.
 */
public class CleanupSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public CleanupSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.admin.cleanup")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        if (args.length < 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(messages.color("&eThis will remove orphaned UUIDs (player files with no known player) from storage. Type /ezeconomy cleanup confirm to proceed."));
            return true;
        }
        Object storage = plugin.getStorageOrWarn();
        Set<String> orphaned = new java.util.HashSet<>();
        // Preview orphaned entries/files
        if (storage instanceof YMLStorageProvider) {
            orphaned = ((YMLStorageProvider) storage).previewOrphanedPlayers();
        } else if (storage instanceof MySQLStorageProvider) {
            orphaned = ((MySQLStorageProvider) storage).previewOrphanedPlayers();
        } else if (storage instanceof SQLiteStorageProvider) {
            orphaned = ((SQLiteStorageProvider) storage).previewOrphanedPlayers();
        } else if (storage instanceof MongoDBStorageProvider) {
            orphaned = ((MongoDBStorageProvider) storage).previewOrphanedPlayers();
        }
        if (orphaned.isEmpty()) {
            sender.sendMessage(messages.color(messages.get("cleanup_preview_empty")));
        } else {
            sender.sendMessage(messages.color(messages.get("cleanup_preview", Map.of("entries", String.join(", ", orphaned)))));
            sender.sendMessage(messages.color(messages.get("cleanup_confirm")));
        }
        // Actual cleanup
        Set<String> removed = new java.util.HashSet<>();
        if (storage instanceof YMLStorageProvider) {
            YMLStorageProvider yml = (YMLStorageProvider) storage;
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
        } else if (storage instanceof MySQLStorageProvider) {
            removed = ((MySQLStorageProvider) storage).cleanupOrphanedPlayers();
        } else if (storage instanceof SQLiteStorageProvider) {
            removed = ((SQLiteStorageProvider) storage).cleanupOrphanedPlayers();
        } else if (storage instanceof MongoDBStorageProvider) {
            removed = ((MongoDBStorageProvider) storage).cleanupOrphanedPlayers();
        }
        if (removed.isEmpty()) {
            sender.sendMessage(messages.color(messages.get("cleanup_complete_empty")));
        } else {
            sender.sendMessage(messages.color(messages.get("cleanup_complete", Map.of("entries", String.join(", ", removed)))));
        }
        return true;
    }
}