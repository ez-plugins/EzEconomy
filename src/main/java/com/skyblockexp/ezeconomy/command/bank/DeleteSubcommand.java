package com.skyblockexp.ezeconomy.command.bank;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Subcommand for /bank delete <name>
 */
public class DeleteSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public DeleteSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ezeconomy.bank.delete") && !sender.hasPermission("ezeconomy.bank.admin")) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "usage_bank");
            return true;
        }
        EconomyResponse deleteResponse = plugin.getEconomy().deleteBank(args[0]);
        if (handleEconomyFailure(sender, deleteResponse)) {
            return true;
        }
        com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "bank_deleted", Map.of("name", args[0]));
        return true;
    }
    private boolean handleEconomyFailure(CommandSender sender, EconomyResponse response) {
        if (response == null || response.type == EconomyResponse.ResponseType.FAILURE
            || response.type == EconomyResponse.ResponseType.NOT_IMPLEMENTED) {
            String message = response == null ? null : response.errorMessage;
            if (message == null || message.isBlank()) {
                message = com.skyblockexp.ezeconomy.util.MessageUtils.format(plugin, "bank_operation_failed");
            }
            sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, message));
            return true;
        }
        return false;
    }
}