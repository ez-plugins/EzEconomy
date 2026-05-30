package com.skyblockexp.ezeconomy.command.eco;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.gui.BalanceGui;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Subcommand for /eco gui
 */
public class GuiSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public GuiSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin, "only_players");
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender) && !(((Player) sender).hasPermission("ezeconomy.user.eco.gui") || ((Player) sender).hasPermission("ezeconomy.eco.gui"))) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        Player player = (Player) sender;
        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            MessageUtils.send(player, plugin, "storage_unavailable");
            return true;
        }
        FileConfiguration config = plugin.getConfig();
        Map<String, Double> currencies = new HashMap<>();
        Map<String, Object> currencySection = config.getConfigurationSection("multi-currency.currencies") != null
            ? config.getConfigurationSection("multi-currency.currencies").getValues(false)
            : Collections.emptyMap();
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
        Map<String, Double> banks = new HashMap<>();
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
        BalanceGui.open(plugin, player, currencies, banks);
        return true;
    }
}