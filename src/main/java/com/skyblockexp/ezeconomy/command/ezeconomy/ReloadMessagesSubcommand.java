package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.command.CommandSender;

/**
 * Handles the /ezeconomy reload messages subcommand.
 */
public class ReloadMessagesSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public ReloadMessagesSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.admin.reload")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        plugin.reloadConfig();
        plugin.loadMessageProvider(); // Assuming there's a method to reload MessageProvider
        sender.sendMessage(messages.color(messages.get("reload_messages_success")));
        return true;
    }
}