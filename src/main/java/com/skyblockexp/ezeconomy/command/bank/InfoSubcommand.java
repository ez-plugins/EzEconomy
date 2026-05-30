package com.skyblockexp.ezeconomy.command.bank;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Subcommand for /bank info <name>
 */
public class InfoSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public InfoSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.bank.info") || sender.hasPermission("ezeconomy.bank.info")) && !sender.hasPermission("ezeconomy.bank.admin")) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "usage_bank");
            return true;
        }
        String currency = args.length >= 2 ? args[1] : "dollar";
        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "storage_unavailable");
            return true;
        }
        EconomyResponse balanceResponse = plugin.getEconomy().bankBalance(args[0], currency);
        if (handleEconomyFailure(sender, balanceResponse)) {
            return true;
        }
        double balance = balanceResponse.balance;
        com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "bank_info", Map.of(
            "name", args[0],
            "balance", plugin.getCurrencyFormatter().formatShort(balance, null),
            "currency", currency,
            "members", String.valueOf(storage.getBankMembers(args[0]).size())
        ));
        return true;
    }
    private boolean handleEconomyFailure(CommandSender sender, EconomyResponse response) {
        if (response == null || response.type == EconomyResponse.ResponseType.FAILURE
            || response.type == EconomyResponse.ResponseType.NOT_IMPLEMENTED) {
            String message = response == null ? "Bank operation failed." : response.errorMessage;
            if (message == null || message.isBlank()) {
                message = "Bank operation failed.";
            }
            sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, message));
            return true;
        }
        return false;
    }
}