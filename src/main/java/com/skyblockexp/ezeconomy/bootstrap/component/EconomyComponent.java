package com.skyblockexp.ezeconomy.bootstrap.component;

import com.skyblockexp.ezeconomy.bootstrap.BootstrapComponent;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.Bukkit;
import net.milkbowl.vault.economy.Economy;

public class EconomyComponent implements BootstrapComponent {
    private final EzEconomyPlugin plugin;

    public EconomyComponent(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        com.skyblockexp.ezeconomy.core.VaultEconomyImpl impl = new com.skyblockexp.ezeconomy.core.VaultEconomyImpl(plugin);
        org.bukkit.Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, impl, plugin, org.bukkit.plugin.ServicePriority.Highest);
        plugin.setVaultEconomy(impl);
    }

    @Override
    public void stop() {
        try {
            Economy e = plugin.getVaultEconomy();
            if (e != null) {
                if (e instanceof com.skyblockexp.ezeconomy.core.VaultEconomyImpl) {
                    ((com.skyblockexp.ezeconomy.core.VaultEconomyImpl) e).shutdown();
                }
                Bukkit.getServicesManager().unregister(Economy.class, e);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void reload() {
        // re-register economy if needed
        start();
    }
}
