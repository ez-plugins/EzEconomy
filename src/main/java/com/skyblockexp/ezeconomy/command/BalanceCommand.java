package com.skyblockexp.ezeconomy.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;

public class BalanceCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;

    public BalanceCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.get("only_players"));
                return true;
            }
            Player player = (Player) sender;
            CurrencyPreferenceManager preferenceManager = plugin.getCurrencyPreferenceManager();
            String currency = preferenceManager.getPreferredCurrency(player.getUniqueId());
            StorageProvider storage = (StorageProvider) plugin.getEconomy().getStorage();
            double balance = storage != null ? storage.getBalance(player.getUniqueId(), currency) : plugin.getEconomy().getBalance(player);
            sender.sendMessage(messages.get("your_balance", java.util.Map.of("balance", plugin.getEconomy().format(balance))));
            return true;
        } else if (args.length == 1) {
            if (!sender.hasPermission("ezeconomy.balance.others")) {
                sender.sendMessage(messages.get("no_permission_others_balance"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            CurrencyPreferenceManager preferenceManager = plugin.getCurrencyPreferenceManager();
            String currency = preferenceManager.getPreferredCurrency(target.getUniqueId());
            StorageProvider storage = (StorageProvider) plugin.getEconomy().getStorage();
            double balance = storage != null ? storage.getBalance(target.getUniqueId(), currency) : plugin.getEconomy().getBalance(target);
            sender.sendMessage(messages.get("others_balance", java.util.Map.of("player", target.getName(), "balance", plugin.getEconomy().format(balance))));
            return true;
        }
        sender.sendMessage(messages.get("usage_balance"));
        return true;
    }
}
