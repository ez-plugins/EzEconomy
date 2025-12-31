package com.skyblockexp.ezeconomy.placeholder;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class EzEconomyPlaceholderExpansion extends PlaceholderExpansion {

    private EzEconomyPlugin plugin;

    public EzEconomyPlaceholderExpansion(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "Shadow48402";
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "ezeconomy";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String getRequiredPlugin() {
        return "EzEconomy";
    }

    @Override
    public boolean canRegister() {
        return (plugin = (EzEconomyPlugin) Bukkit.getPluginManager().getPlugin(getRequiredPlugin())) != null;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Usage:
        // %ezeconomy_balance% (default currency)
        // %ezeconomy_balance_<currency>%
        // %ezeconomy_bank_<bank>% (default currency)
        // %ezeconomy_bank_<bank>_<currency>%
        // %ezeconomy_symbol_<currency>%

        var eco = plugin.getEconomy();
        var config = plugin.getConfig();
        boolean multiEnabled = config.getBoolean("multi-currency.enabled", false);
        String defaultCurrency = multiEnabled ? config.getString("multi-currency.default", "dollar") : "dollar";
        String preferredCurrency = defaultCurrency;
        if (player != null) {
            preferredCurrency = plugin.getCurrencyPreferenceManager().getPreferredCurrency(player.getUniqueId());
        }
        com.skyblockexp.ezeconomy.storage.StorageProvider storage = plugin.getStorageOrWarn();

        String[] split = params.toLowerCase().split("_");
        try {
            if (params.equalsIgnoreCase("balance")) {
                if (player == null || storage == null) {
                    return null;
                }
                double bal = storage.getBalance(player.getUniqueId(), preferredCurrency);
                return eco.format(bal);
            }
            if (split.length == 2 && split[0].equals("balance")) {
                if (player == null || storage == null) {
                    return null;
                }
                String currency = split[1];
                double bal = storage.getBalance(player.getUniqueId(), currency);
                return eco.format(bal);
            }
            if (split.length == 2 && split[0].equals("symbol")) {
                String currency = split[1];
                if (multiEnabled && config.contains("multi-currency.currencies." + currency + ".symbol")) {
                    return config.getString("multi-currency.currencies." + currency + ".symbol", "$");
                } else if (currency.equals("dollar")) {
                    return "$";
                }
                return "?";
            }
            if (split.length >= 2 && split[0].equals("bank")) {
                if (player == null || storage == null) {
                    return null;
                }
                String bank = split[1];
                String currency = (split.length == 3) ? split[2] : preferredCurrency;
                if (storage.isBankMember(bank, player.getUniqueId())) {
                    double bal = storage.getBankBalance(bank, currency);
                    return eco.format(bal);
                } else {
                    return "-";
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
