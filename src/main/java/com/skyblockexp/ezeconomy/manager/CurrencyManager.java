package com.skyblockexp.ezeconomy.manager;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;

public class CurrencyManager {
    private final EzEconomyPlugin plugin;
    public CurrencyManager(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }
    public String getDefaultCurrency() {
        var config = plugin.getConfig();
        boolean multiEnabled = config.getBoolean("multi-currency.enabled", false);
        return multiEnabled ? config.getString("multi-currency.default", "dollar") : "dollar";
    }
}