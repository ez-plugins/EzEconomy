package com.skyblockexp.ezeconomy.command.bank;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.NumberUtil;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.CommandSender;

/**
 * Subcommand for /bank withdraw <name> <amount> [currency]
 */
public class WithdrawSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public WithdrawSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.bank.withdraw") || sender.hasPermission("ezeconomy.bank.withdraw")) && !sender.hasPermission("ezeconomy.bank.admin")) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "usage_bank_withdraw");
            return true;
        }

        // Resolve bank name and amount:
        // /bank withdraw <amount>          — withdraws from the sender's own bank
        // /bank withdraw <name> <amount>   — withdraws from the named bank
        final String bankName;
        final String amountStr;
        final String currency;
        if (args.length == 1) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "only_players");
                return true;
            }
            bankName = sender.getName();
            amountStr = args[0];
            currency = plugin.getDefaultCurrency();
        } else {
            bankName = args[0];
            amountStr = args[1];
            currency = args.length >= 3 ? args[2] : plugin.getDefaultCurrency();
        }

        Double amount = NumberUtil.parseDouble(amountStr);
        if (amount == null || amount <= 0) {
            com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "invalid_amount");
            return true;
        }
        EconomyResponse withdrawResponse = plugin.getEconomy().bankWithdraw(bankName, currency, amount);
        if (handleEconomyFailure(sender, withdrawResponse, bankName)) {
            return true;
        }

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) return true;
        org.bukkit.entity.Player playerSender = sender instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) sender : null;
        if (playerSender != null) {
            try {
                storage.deposit(playerSender.getUniqueId(), currency, amount);
            } catch (Exception ex) {
                // Roll back the bank withdrawal if wallet credit fails unexpectedly.
                storage.depositBank(bankName, currency, amount);
                com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "bank_operation_failed");
                return true;
            }
        }

        String formattedAmount = plugin.getCurrencyFormatter().formatPriceForMessage(amount, currency);
        java.util.HashMap<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("name", bankName);
        placeholders.put("amount", formattedAmount);
        placeholders.put("currency", currency);
        com.skyblockexp.ezeconomy.util.MessageUtils.send(sender, plugin, "withdrew", placeholders);
        return true;
    }
    private boolean handleEconomyFailure(CommandSender sender, EconomyResponse response, String bankName) {
        if (response == null || response.type == EconomyResponse.ResponseType.FAILURE
            || response.type == EconomyResponse.ResponseType.NOT_IMPLEMENTED) {
            String message = response == null ? null : response.errorMessage;
            String fallback = (message == null || message.isBlank())
                    ? com.skyblockexp.ezeconomy.util.MessageUtils.format(plugin, "bank_operation_failed")
                    : message;
            sender.sendMessage(com.skyblockexp.ezeconomy.util.MessageUtils.color(plugin, fallback));
            return true;
        }
        return false;
    }
}
