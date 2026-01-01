
package com.skyblockexp.ezeconomy.command;

import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.util.NumberUtil;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.storage.TransferResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;

    public PayCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.color(messages.get("only_players")));
            return true;
        }
        if (!sender.hasPermission("ezeconomy.pay")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.color(messages.get("usage_pay")));
            return true;
        }
        Player from = (Player) sender;
        OfflinePlayer to = Bukkit.getOfflinePlayer(args[0]);
        if (to == null || !to.hasPlayedBefore()) {
            sender.sendMessage(messages.color(messages.get("player_not_found")));
            return true;
        }
        double amount = NumberUtil.parseAmount(args[1]);
        if (Double.isNaN(amount)) {
            sender.sendMessage(messages.color(messages.get("invalid_amount")));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(messages.color(messages.get("must_be_positive")));
            return true;
        }
        if (from.getUniqueId().equals(to.getUniqueId())) {
            sender.sendMessage(messages.color(messages.get("cannot_pay_self")));
            return true;
        }
            double netAmount = amount;
            StorageProvider storage = plugin.getStorageOrWarn();
            if (storage == null) {
                return true;
            }
            TransferResult transfer = storage.transfer(from.getUniqueId(), to.getUniqueId(), plugin.getDefaultCurrency(), amount, netAmount);
            if (!transfer.isSuccess()) {
                sender.sendMessage(messages.color(messages.get("not_enough_money")));
                return true;
            }
            sender.sendMessage(messages.color(messages.get("paid", java.util.Map.of(
                "player", to.getName(),
                "amount", plugin.getEconomy().format(netAmount)
            ))));
            if (to.isOnline() && to.getPlayer() != null) {
                to.getPlayer().sendMessage(messages.color(messages.get("received", java.util.Map.of(
                    "player", from.getName(),
                    "amount", plugin.getEconomy().format(netAmount)
                ))));
            }
            return true;
    }
}
