package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Handles the /ezeconomy daily reset subcommand.
 */
public class DailyResetSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;
    private final DailyRewardManager dailyRewardManager;

    public DailyResetSubcommand(EzEconomyPlugin plugin, DailyRewardManager dailyRewardManager) {
        this.plugin = plugin;
        this.dailyRewardManager = dailyRewardManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.admin.daily")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.color(messages.get("usage_daily_reset")));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        dailyRewardManager.resetReward(target.getUniqueId());
        sender.sendMessage(messages.color(messages.get("daily_reset", Map.of(
                "player", target.getName() == null ? args[0] : target.getName()
        ))));
        return true;
    }
}