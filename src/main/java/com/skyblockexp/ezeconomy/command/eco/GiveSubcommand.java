package com.skyblockexp.ezeconomy.command.eco;

import com.skyblockexp.ezeconomy.command.Subcommand;

/**
 * Subcommand for /eco give <player> <amount>
 */
public class GiveSubcommand implements Subcommand {
    private final com.skyblockexp.ezeconomy.core.EzEconomyPlugin plugin;

    public GiveSubcommand(com.skyblockexp.ezeconomy.core.EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(org.bukkit.command.CommandSender sender, String[] args) {
        com.skyblockexp.ezeconomy.core.MessageProvider messages = plugin.getMessageProvider();
        if (args.length < 2) {
            sender.sendMessage(messages.color(messages.get("usage_eco")));
            return true;
        }
        org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
        double amount = com.skyblockexp.ezeconomy.util.NumberUtil.parseAmount(args[1]);
        if (Double.isNaN(amount)) {
            sender.sendMessage(messages.color(messages.get("invalid_amount")));
            return true;
        }
        plugin.getEconomy().depositPlayer(target, amount);
        sender.sendMessage(messages.color(messages.get("paid", java.util.Map.of("player", target.getName(), "amount", plugin.getEconomy().format(amount)))));
        return true;
    }
}