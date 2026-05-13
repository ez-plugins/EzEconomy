package com.skyblockexp.ezeconomy.teams;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.teams.command.TeamBankCommand;
import com.skyblockexp.teamsapi.api.TeamsAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the EzEconomy-Teams add-on module.
 *
 * <p>Bridges EzEconomy bank storage with a TeamsAPI provider (e.g. pvpindex-factions)
 * so every team automatically gets a shared bank accessible via {@code /teambank}
 * (alias: {@code /factionbank}).</p>
 */
public class EzTeamsPlugin extends JavaPlugin {

    private EzEconomyPlugin economy;

    @Override
    public void onEnable() {
        if (!TeamsAPI.isAvailable()) {
            getLogger().warning("No TeamsAPI provider found. EzEconomy-Teams will remain disabled.");
            getLogger().warning("Install a TeamsAPI-compatible plugin (e.g. pvpindex-factions) and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = (EzEconomyPlugin) Bukkit.getPluginManager().getPlugin("EzEconomy");
        if (economy == null || !economy.isEnabled()) {
            getLogger().severe("EzEconomy plugin not found or not enabled. EzEconomy-Teams will remain disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        TeamBankCommand cmd = new TeamBankCommand(economy);
        getCommand("teambank").setExecutor(cmd);

        getLogger().info("EzEconomy-Teams enabled. Team banks are ready.");
    }

    @Override
    public void onDisable() {
        getLogger().info("EzEconomy-Teams disabled.");
    }

    /**
     * Returns the EzEconomy plugin instance used by this add-on.
     */
    public EzEconomyPlugin getEconomy() {
        return economy;
    }
}
