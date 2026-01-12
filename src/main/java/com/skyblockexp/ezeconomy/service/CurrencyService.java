package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.manager.CurrencyManager;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

/**
 * Service for currency management and queries.
 */
public class CurrencyService {
    private final StorageProvider storageProvider;

    public CurrencyService(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getDefaultCurrency() {
        if (storageProvider instanceof EzEconomyPlugin plugin) {
            CurrencyManager cm = plugin.getCurrencyManager();
            if (cm != null) return cm.getDefaultCurrency();
        }
        return "dollar";
    }

    public Set<String> getAvailableCurrencies() {
        if (storageProvider instanceof EzEconomyPlugin plugin) {
            FileConfiguration config = plugin.getConfig();
            if (config.isConfigurationSection("multi-currency.currencies")) {
                return config.getConfigurationSection("multi-currency.currencies").getKeys(false);
            }
        }
        return Set.of("dollar");
    }

    public boolean isCurrencyEnabled(String currency) {
        return getAvailableCurrencies().contains(currency);
    }
}
