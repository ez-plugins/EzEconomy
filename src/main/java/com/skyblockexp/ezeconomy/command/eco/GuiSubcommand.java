package com.skyblockexp.ezeconomy.command.eco;

import com.skyblockexp.ezeconomy.command.Subcommand;

/**
 * Subcommand for /eco gui
 */
public class GuiSubcommand implements Subcommand {
    private final com.skyblockexp.ezeconomy.core.EzEconomyPlugin plugin;

    public GuiSubcommand(com.skyblockexp.ezeconomy.core.EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(org.bukkit.command.CommandSender sender, String[] args) {
        com.skyblockexp.ezeconomy.core.MessageProvider messages = plugin.getMessageProvider();
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(messages.color(messages.get("only_players")));
            return true;
        }
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        com.skyblockexp.ezeconomy.api.storage.StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            player.sendMessage(messages.color(messages.get("storage_unavailable")));
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
        com.skyblockexp.ezeconomy.gui.BalanceGui.open(player, currencies, banks);
        return true;
    }
}