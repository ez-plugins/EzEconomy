package com.skyblockexp.ezeconomy.command.bank;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Subcommand for /bank removemember <name> <player>
 */
public class RemoveMemberSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public RemoveMemberSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.bank.removemember") && !sender.hasPermission("ezeconomy.bank.admin")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.color(messages.get("usage_bank")));
            return true;
        }
        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            sender.sendMessage(messages.color(messages.get("storage_unavailable")));
            return true;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
        storage.removeBankMember(args[0], player.getUniqueId());
        sender.sendMessage(messages.color(messages.get("removed_member", Map.of("player", player.getName(), "name", args[0]))));
        return true;
    }
}