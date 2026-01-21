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
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.bank.delete") && !sender.hasPermission("ezeconomy.bank.admin")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.color(messages.get("usage_bank")));
            return true;
        }
        EconomyResponse deleteResponse = plugin.getEconomy().deleteBank(args[0]);
        if (handleEconomyFailure(sender, deleteResponse, messages)) {
            return true;
        }
        sender.sendMessage(messages.color(messages.get("bank_deleted", Map.of("name", args[0]))));
        return true;
    }

    private boolean handleEconomyFailure(CommandSender sender, EconomyResponse response, MessageProvider messages) {
        if (response == null || response.type == EconomyResponse.ResponseType.FAILURE
            || response.type == EconomyResponse.ResponseType.NOT_IMPLEMENTED) {
            String message = response == null ? "Bank operation failed." : response.errorMessage;
            if (message == null || message.isBlank()) {
                message = "Bank operation failed.";
            }
            sender.sendMessage(messages.color(message));
            return true;
        }
        return false;
    }
}