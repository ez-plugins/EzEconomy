package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.storage.mysql.MySQLBalanceBackgroundPersistenceService;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import org.bukkit.command.CommandSender;

/**
 * Handles /ezeconomy spool subcommands for MySQL fallback spool visibility and replay.
 */
public class SpoolSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public SpoolSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ezeconomy.database.spool")) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(MessageUtils.color(plugin, "&eUsage: &f/ezeconomy spool <size|replay>"));
            return true;
        }

        Object storage = plugin.getStorageOrWarn();
        if (!(storage instanceof MySQLStorageProvider)) {
            sender.sendMessage(MessageUtils.color(plugin, "&cSpool commands are available only when storage type is MySQL."));
            return true;
        }

        MySQLStorageProvider mysql = (MySQLStorageProvider) storage;
        String action = args[0].toLowerCase();
        if ("size".equals(action)) {
            int size = mysql.getLocalSpoolSize();
            if (size < 0) {
                sender.sendMessage(MessageUtils.color(plugin, "&cUnable to read local spool size."));
            } else {
                sender.sendMessage(MessageUtils.color(plugin, "&6[EzEconomy] &eLocal MySQL spool rows: &f" + size));
            }
            return true;
        }

        if ("replay".equals(action)) {
            if (!sender.hasPermission("ezeconomy.database.spool.replay")) {
                MessageUtils.send(sender, plugin, "no_permission");
                return true;
            }
            sender.sendMessage(MessageUtils.color(plugin, "&eReplaying local spool rows into MySQL..."));
            MySQLBalanceBackgroundPersistenceService.ReplayResult result = mysql.replayLocalSpoolNow();
            if (result.isSuccess()) {
                sender.sendMessage(MessageUtils.color(plugin,
                        "&aReplay complete. Replayed rows: &f" + result.getReplayedRows() + " &a| Remaining rows: &f" + result.getRemainingRows()));
            } else {
                sender.sendMessage(MessageUtils.color(plugin,
                        "&cReplay incomplete. Replayed rows: &f" + result.getReplayedRows() + " &c| Remaining rows: &f" + result.getRemainingRows()));
            }
            return true;
        }

        sender.sendMessage(MessageUtils.color(plugin, "&eUsage: &f/ezeconomy spool <size|replay>"));
        return true;
    }
}
