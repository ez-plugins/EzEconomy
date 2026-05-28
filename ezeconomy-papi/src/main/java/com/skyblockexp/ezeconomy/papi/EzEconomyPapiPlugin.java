package com.skyblockexp.ezeconomy.papi;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EzEconomyPapiPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found; disabling EzEconomy-PAPI expansion.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        org.bukkit.plugin.Plugin ezEconomy = Bukkit.getPluginManager().getPlugin("EzEconomy");
        if (ezEconomy == null || !ezEconomy.isEnabled()) {
            getLogger().warning("EzEconomy is not enabled; disabling EzEconomy-PAPI expansion.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register the expansion
        new EzEconomyPAPIExpansion(this).register();
        getLogger().info("EzEconomy-PAPI expansion enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("EzEconomy-PAPI expansion disabled.");
    }
}
