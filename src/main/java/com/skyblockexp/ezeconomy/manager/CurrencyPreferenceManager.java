package com.skyblockexp.ezeconomy.manager;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class CurrencyPreferenceManager {
    private static final String PREFERENCES_PATH = "preferences";

    private final EzEconomyPlugin plugin;
    private final File dataFile;
    private final Map<UUID, String> preferences = new ConcurrentHashMap<>();
    private YamlConfiguration config;

    public CurrencyPreferenceManager(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "currency-preferences.yml");
        load();
    }

    public String getPreferredCurrency(UUID uuid) {
        return preferences.getOrDefault(uuid, getDefaultCurrency());
    }

    public void setPreferredCurrency(UUID uuid, String currency) {
        preferences.put(uuid, currency);
        persist(uuid, currency);
    }

    public void load() {
        config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection(PREFERENCES_PATH);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String currency = section.getString(key);
                if (currency != null && !currency.isBlank()) {
                    preferences.put(uuid, currency.toLowerCase());
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in currency preferences: " + key);
            }
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        for (Map.Entry<UUID, String> entry : preferences.entrySet()) {
            config.set(PREFERENCES_PATH + "." + entry.getKey(), entry.getValue());
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save currency preferences: " + e.getMessage());
        }
    }

    private void persist(UUID uuid, String currency) {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set(PREFERENCES_PATH + "." + uuid, currency);
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save currency preferences: " + e.getMessage());
        }
    }

    private String getDefaultCurrency() {
        var config = plugin.getConfig();
        boolean multiEnabled = config.getBoolean("multi-currency.enabled", false);
        return multiEnabled ? config.getString("multi-currency.default", "dollar") : "dollar";
    }
}
