package com.skyblockexp.ezeconomy.command;

import com.skyblockexp.ezeconomy.command.bank.*;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the /bank command and its subcommands.
 */
public class BankCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;
    private final Map<String, com.skyblockexp.ezeconomy.command.Subcommand> subcommands;

    public BankCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.subcommands = new HashMap<>();
        this.subcommands.put("create", new CreateSubcommand(plugin));
        this.subcommands.put("delete", new DeleteSubcommand(plugin));
        this.subcommands.put("balance", new BalanceSubcommand(plugin));
        this.subcommands.put("deposit", new DepositSubcommand(plugin));
        this.subcommands.put("withdraw", new WithdrawSubcommand(plugin));
        this.subcommands.put("addmember", new AddMemberSubcommand(plugin));
        this.subcommands.put("removemember", new RemoveMemberSubcommand(plugin));
        this.subcommands.put("info", new InfoSubcommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (args.length == 0) {
            sender.sendMessage(messages.color(messages.get("usage_bank")));
            return true;
        }

        String subcommandKey = args[0].toLowerCase();
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);

        com.skyblockexp.ezeconomy.command.Subcommand subcommand = subcommands.get(subcommandKey);
        if (subcommand != null) {
            return subcommand.execute(sender, subArgs);
        }

        // Unknown subcommand
        sender.sendMessage(messages.color(messages.get("unknown_subcommand")));
        return true;
    }
}

