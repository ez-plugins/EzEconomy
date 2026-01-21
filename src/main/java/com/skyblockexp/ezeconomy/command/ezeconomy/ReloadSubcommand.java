package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.command.CommandSender;

/**
 * Handles the /ezeconomy reload subcommand to reload all configurations.
 */
public class ReloadSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public ReloadSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.admin.reload")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        // Reload main config
        plugin.reloadConfig();
        // Reload messages
        plugin.loadMessageProvider();
        // TODO: Reload storage config if needed
        sender.sendMessage(messages.color(messages.get("reload_success")));
        return true;
    }
}