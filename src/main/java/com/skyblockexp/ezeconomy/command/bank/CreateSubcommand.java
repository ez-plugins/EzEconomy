package com.skyblockexp.ezeconomy.command.bank;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Subcommand for /bank create <name>
 */
public class CreateSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public CreateSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.bank.create") || sender.hasPermission("ezeconomy.bank.create")) && !sender.hasPermission("ezeconomy.bank.admin")) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtils.send(sender, plugin, "usage_bank_create");
            return true;
        }
        if (!(sender instanceof OfflinePlayer)) {
            MessageUtils.send(sender, plugin, "only_players");
            return true;
        }
        String name = args[0];
        OfflinePlayer owner = (OfflinePlayer) sender;
        EconomyResponse createResponse = plugin.getEconomy().createBank(name, owner);
        if (handleEconomyFailure(sender, createResponse)) {
            return true;
        }
        MessageUtils.send(sender, plugin, "bank_created", Map.of("name", name, "owner", owner.getName()));
        return true;
    }

    private boolean handleEconomyFailure(CommandSender sender, EconomyResponse response) {
        if (response == null || response.type == EconomyResponse.ResponseType.FAILURE
            || response.type == EconomyResponse.ResponseType.NOT_IMPLEMENTED) {
            String message = response == null ? null : response.errorMessage;
            if (message == null || message.isBlank()) {
                message = MessageUtils.format(plugin, "bank_operation_failed");
            }
            sender.sendMessage(MessageUtils.color(plugin, message));
            return true;
        }
        return false;
    }
}