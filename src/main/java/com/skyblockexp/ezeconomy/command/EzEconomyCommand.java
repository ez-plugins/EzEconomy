package com.skyblockexp.ezeconomy.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

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
        sender.sendMessage(messages.color(messages.get("unknown_subcommand")));
        return true;
    }
}
