package com.skyblockexp.ezeconomy.command;


import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.skyblockexp.ezeconomy.gui.BalanceGui;
import org.bukkit.entity.Player;

import com.skyblockexp.ezeconomy.gui.BalanceGui;


import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import com.skyblockexp.ezeconomy.util.NumberUtil;

public class EcoCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;

    public EcoCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.eco")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messages.color("&cOnly players can use this command."));
                return true;
            }
            Player player = (Player) sender;
            com.skyblockexp.ezeconomy.api.storage.StorageProvider storage = plugin.getStorageOrWarn();
            if (storage == null) {
                player.sendMessage(messages.color("&cStorage provider unavailable. Check server logs."));
                return true;
            }
            org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
            java.util.Map<String, Double> currencies = new java.util.HashMap<>();
            java.util.Map<String, Object> currencySection = config.getConfigurationSection("multi-currency.currencies") != null
                ? config.getConfigurationSection("multi-currency.currencies").getValues(false)
                : java.util.Collections.emptyMap();
            if (config.getBoolean("multi-currency.enabled", false) && !currencySection.isEmpty()) {
                for (String currency : currencySection.keySet()) {
                    double balance = storage.getBalance(player.getUniqueId(), currency);
                    currencies.put(currency, balance);
                }
            } else {
                String currency = plugin.getDefaultCurrency();
                double balance = storage.getBalance(player.getUniqueId(), currency);
                currencies.put(currency, balance);
            }
            // Banks (show for all currencies)
            java.util.Map<String, Double> banks = new java.util.HashMap<>();
            for (String bank : storage.getBanks()) {
                if (storage.isBankMember(bank, player.getUniqueId())) {
                    if (config.getBoolean("multi-currency.enabled", false) && !currencySection.isEmpty()) {
                        for (String currency : currencySection.keySet()) {
                            double bankBalance = storage.getBankBalance(bank, currency);
                            banks.put(bank + " (" + currency + ")", bankBalance);
                        }
                    } else {
                        String currency = plugin.getDefaultCurrency();
                        double bankBalance = storage.getBankBalance(bank, currency);
                        banks.put(bank, bankBalance);
                    }
                }
            }
            BalanceGui.open(player, currencies, banks);
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(messages.color(messages.get("usage_eco")));
            return true;
        }
        String action = args[0].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount;
        amount = NumberUtil.parseAmount(args[2]);
        if (Double.isNaN(amount)) {
            sender.sendMessage(messages.color(messages.get("invalid_amount")));
            return true;
        }
        switch (action) {
            case "give":
                plugin.getEconomy().depositPlayer(target, amount);
                sender.sendMessage(messages.color(messages.get("paid", java.util.Map.of("player", target.getName(), "amount", plugin.getEconomy().format(amount)))));
                break;
            case "take":
                plugin.getEconomy().withdrawPlayer(target, amount);
                sender.sendMessage(messages.color(messages.get("withdrew", java.util.Map.of("name", target.getName(), "amount", plugin.getEconomy().format(amount)))));
                break;
            case "set":
                com.skyblockexp.ezeconomy.api.storage.StorageProvider storage = plugin.getStorageOrWarn();
                if (storage == null) {
                    sender.sendMessage(messages.color("&cStorage provider unavailable. Check server logs."));
                    return true;
                }
                storage.setBalance(target.getUniqueId(), plugin.getDefaultCurrency(), amount);
                sender.sendMessage(messages.color(messages.get("set", java.util.Map.of("player", target.getName(), "balance", plugin.getEconomy().format(amount)))));
                break;
            default:
                sender.sendMessage(messages.color(messages.get("unknown_action")));
        }
        return true;
    }
}
