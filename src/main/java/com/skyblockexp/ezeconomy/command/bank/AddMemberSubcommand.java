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
 * Subcommand for /bank addmember <name> <player>
 */
public class AddMemberSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public AddMemberSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.bank.addmember") || sender.hasPermission("ezeconomy.bank.addmember")) && !sender.hasPermission("ezeconomy.bank.admin")) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 2) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "usage_bank");
            return true;
        }
        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "storage_unavailable");
            return true;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
        storage.addBankMember(args[0], player.getUniqueId());
        com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "added_member", Map.of("player", player.getName(), "name", args[0]));
        return true;
    }
}