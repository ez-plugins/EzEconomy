package com.skyblockexp.ezeconomy.tabcomplete;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BankTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ezeconomy.bank")) return Collections.emptyList();
        // /bank <sub> <bank> <amount> <currency>
        if (args.length == 1) {
            // Suggest subcommands
            List<String> subs = List.of("create", "delete", "balance", "deposit", "withdraw", "addmember", "removemember", "info");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            // Suggest bank names (stub: could fetch from storage)
            // For demo, return empty or static list
            return Collections.emptyList();
        }
        if (args.length == 3) {
            // For deposit/withdraw, suggest amounts including shorthand
            if (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("withdraw")) {
                return List.of("100", "1k", "10k", "100k", "1m", "10m", "100m", "1b");
            }
            return Collections.emptyList();
        }
        if (args.length == 4) {
            // Suggest currencies (stub: could fetch from config)
            List<String> currencies = List.of("dollar", "coin", "gem");
            return currencies.stream().filter(c -> c.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}