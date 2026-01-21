package com.skyblockexp.ezeconomy.command;

import com.skyblockexp.ezeconomy.command.ezeconomy.CleanupSubcommand;
import com.skyblockexp.ezeconomy.command.ezeconomy.DatabaseResetSubcommand;
import com.skyblockexp.ezeconomy.command.ezeconomy.DatabaseSubcommand;
import com.skyblockexp.ezeconomy.command.ezeconomy.DatabaseTestSubcommand;
import com.skyblockexp.ezeconomy.command.ezeconomy.DailyResetSubcommand;
import com.skyblockexp.ezeconomy.command.ezeconomy.ReloadMessagesSubcommand;
import com.skyblockexp.ezeconomy.command.ezeconomy.ReloadSubcommand;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the /ezeconomy admin command and its subcommands.
 * Includes daily reward reset and orphaned player data cleanup for all supported storage types.
 *
 * This class is part of the open-source EzEconomy project.
 */
public class EzEconomyCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;
    private final DailyRewardManager dailyRewardManager;
    private final Map<String, Subcommand> subcommands;

    public EzEconomyCommand(EzEconomyPlugin plugin, DailyRewardManager dailyRewardManager) {
        this.plugin = plugin;
        this.dailyRewardManager = dailyRewardManager;
        this.subcommands = new HashMap<>();
        this.subcommands.put("cleanup", new CleanupSubcommand(plugin));
        this.subcommands.put("dailyreset", new DailyResetSubcommand(plugin, dailyRewardManager));
        this.subcommands.put("reload", new ReloadSubcommand(plugin));
        this.subcommands.put("reloadmessages", new ReloadMessagesSubcommand(plugin));
        // Single database subcommand handles info, test, and reset
        this.subcommands.put("database", new DatabaseSubcommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (args.length == 0) {
            sender.sendMessage(messages.color(messages.get("usage_ezeconomy")));
            return true;
        }

        String subcommandKey = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        // Handle multi-level subcommands
        if (subcommandKey.equals("daily") && subArgs.length > 0 && subArgs[0].equalsIgnoreCase("reset")) {
            subcommandKey = "dailyreset";
            subArgs = Arrays.copyOfRange(subArgs, 1, subArgs.length);
        } else if (subcommandKey.equals("reload") && subArgs.length > 0 && subArgs[0].equalsIgnoreCase("messages")) {
            subcommandKey = "reloadmessages";
            subArgs = Arrays.copyOfRange(subArgs, 1, subArgs.length);
        }

        // All database subcommands handled by DatabaseSubcommand
        if (subcommandKey.equals("database")) {
            Subcommand subcommand = subcommands.get("database");
            if (subcommand != null) {
                return subcommand.execute(sender, subArgs);
            }
        } else {
            Subcommand subcommand = subcommands.get(subcommandKey);
            if (subcommand != null) {
                return subcommand.execute(sender, subArgs);
            }
        }

        // Unknown subcommand
        sender.sendMessage(messages.color(messages.get("unknown_subcommand")));
        return true;
    }
}
