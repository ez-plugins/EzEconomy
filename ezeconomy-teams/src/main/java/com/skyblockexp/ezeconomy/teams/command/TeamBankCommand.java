package com.skyblockexp.ezeconomy.teams.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the /teambank (alias /factionbank) command and dispatches to subcommands.
 */
public class TeamBankCommand implements CommandExecutor {

    private final Map<String, TeamBankSubcommand> subcommands;
    private final EzEconomyPlugin plugin;

    public TeamBankCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.subcommands = new HashMap<>();
        this.subcommands.put("balance", new BalanceSubcommand(plugin));
        this.subcommands.put("deposit", new DepositSubcommand(plugin));
        this.subcommands.put("withdraw", new WithdrawSubcommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            MessageUtils.send(sender, plugin, "usage_teambank");
            return true;
        }

        String key = args[0].toLowerCase();
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);

        TeamBankSubcommand sub = subcommands.get(key);
        if (sub != null) {
            return sub.execute(sender, subArgs);
        }

        MessageUtils.send(sender, plugin, "unknown_subcommand");
        return true;
    }
}
