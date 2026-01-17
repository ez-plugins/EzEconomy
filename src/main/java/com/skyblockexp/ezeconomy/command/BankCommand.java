package com.skyblockexp.ezeconomy.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.milkbowl.vault.economy.EconomyResponse;
import java.util.UUID;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;

public class BankCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;

    public BankCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (args.length < 1) {
            sender.sendMessage(messages.color(messages.get("usage_bank")));
            return true;
        }
        String sub = args[0].toLowerCase();
        String currency = "dollar";
        // For commands that support currency, allow it as the last argument
        switch (sub) {
            case "create": {
                if (!sender.hasPermission("ezeconomy.bank.create") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                if (!(sender instanceof OfflinePlayer)) {
                    sender.sendMessage(messages.color(messages.get("only_players")));
                    return true;
                }
                String name = args[1];
                OfflinePlayer owner = (OfflinePlayer) sender;
                EconomyResponse createResponse = plugin.getEconomy().createBank(name, owner);
                if (handleEconomyFailure(sender, createResponse, messages)) {
                    return true;
                }
                sender.sendMessage(messages.color(messages.get("bank_created", java.util.Map.of("name", name))));
                break;
            }
            case "delete": {
                if (!sender.hasPermission("ezeconomy.bank.delete") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                EconomyResponse deleteResponse = plugin.getEconomy().deleteBank(args[1]);
                if (handleEconomyFailure(sender, deleteResponse, messages)) {
                    return true;
                }
                sender.sendMessage(messages.color(messages.get("bank_deleted", java.util.Map.of("name", args[1]))));
                break;
            }
            case "balance": {
                if (!sender.hasPermission("ezeconomy.bank.balance") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                if (args.length >= 3) currency = args[2];
                EconomyResponse balanceResponse = plugin.getEconomy().bankBalance(args[1], currency);
                if (handleEconomyFailure(sender, balanceResponse, messages)) {
                    return true;
                }
                double bal = balanceResponse.balance;
                sender.sendMessage(messages.color(messages.get("bank_balance", java.util.Map.of("name", args[1], "balance", plugin.getEconomy().format(bal), "currency", currency))));
                break;
            }
            case "deposit": {
                if (!sender.hasPermission("ezeconomy.bank.deposit") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                double dep;
                try { dep = Double.parseDouble(args[2]); } catch (NumberFormatException e) { sender.sendMessage(messages.color(messages.get("invalid_amount"))); return true; }
                if (dep <= 0) {
                    sender.sendMessage(messages.color(messages.get("must_be_positive")));
                    return true;
                }
                if (args.length >= 4) currency = args[3];
                EconomyResponse depositResponse = plugin.getEconomy().bankDeposit(args[1], currency, dep);
                if (handleEconomyFailure(sender, depositResponse, messages)) {
                    return true;
                }
                sender.sendMessage(messages.color(messages.get("deposited", java.util.Map.of(
                    "amount", plugin.getEconomy().format(dep),
                    "name", args[1],
                    "currency", currency
                ))));
                break;
            }
            case "withdraw": {
                if (!sender.hasPermission("ezeconomy.bank.withdraw") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                double wd;
                try { wd = Double.parseDouble(args[2]); } catch (NumberFormatException e) { sender.sendMessage(messages.color(messages.get("invalid_amount"))); return true; }
                if (wd <= 0) {
                    sender.sendMessage(messages.color(messages.get("must_be_positive")));
                    return true;
                }
                if (args.length >= 4) currency = args[3];
                EconomyResponse withdrawResponse = plugin.getEconomy().bankWithdraw(args[1], currency, wd);
                if (handleEconomyFailure(sender, withdrawResponse, messages)) {
                    return true;
                }
                sender.sendMessage(messages.color(messages.get("withdrew", java.util.Map.of(
                    "amount", plugin.getEconomy().format(wd),
                    "name", args[1],
                    "currency", currency
                ))));
                break;
            }
            case "addmember": {
                if (!sender.hasPermission("ezeconomy.bank.addmember") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                com.skyblockexp.ezeconomy.api.storage.StorageProvider storageAdd = plugin.getStorageOrWarn();
                if (storageAdd == null) {
                    sender.sendMessage(messages.color(messages.get("storage_unavailable")));
                    return true;
                }
                OfflinePlayer add = Bukkit.getOfflinePlayer(args[2]);
                storageAdd.addBankMember(args[1], add.getUniqueId());
                sender.sendMessage(messages.color(messages.get("added_member", java.util.Map.of("player", add.getName(), "name", args[1]))));
                break;
            }
            case "removemember": {
                if (!sender.hasPermission("ezeconomy.bank.removemember") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                com.skyblockexp.ezeconomy.api.storage.StorageProvider storageRemove = plugin.getStorageOrWarn();
                if (storageRemove == null) {
                    sender.sendMessage(messages.color(messages.get("storage_unavailable")));
                    return true;
                }
                OfflinePlayer rem = Bukkit.getOfflinePlayer(args[2]);
                storageRemove.removeBankMember(args[1], rem.getUniqueId());
                sender.sendMessage(messages.color(messages.get("removed_member", java.util.Map.of("player", rem.getName(), "name", args[1]))));
                break;
            }
            case "info": {
                if (!sender.hasPermission("ezeconomy.bank.info") && !sender.hasPermission("ezeconomy.bank.admin")) {
                    sender.sendMessage(messages.color(messages.get("no_permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messages.color(messages.get("usage_bank")));
                    return true;
                }
                if (args.length >= 3) currency = args[2];
                com.skyblockexp.ezeconomy.api.storage.StorageProvider storageInfo = plugin.getStorageOrWarn();
                if (storageInfo == null) {
                    sender.sendMessage(messages.color(messages.get("storage_unavailable")));
                    return true;
                }
                EconomyResponse infoBalanceResponse = plugin.getEconomy().bankBalance(args[1], currency);
                if (handleEconomyFailure(sender, infoBalanceResponse, messages)) {
                    return true;
                }
                double infoBalance = infoBalanceResponse.balance;
                sender.sendMessage(messages.color(messages.get("bank_info", java.util.Map.of(
                    "name", args[1],
                    "balance", plugin.getEconomy().format(infoBalance),
                    "currency", currency,
                    "members", String.valueOf(storageInfo.getBankMembers(args[1]).size())
                ))));
                break;
            }
            default:
                sender.sendMessage(messages.color(messages.get("unknown_subcommand")));
        }
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
