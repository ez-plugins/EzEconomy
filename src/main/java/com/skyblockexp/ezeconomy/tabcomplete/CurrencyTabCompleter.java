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
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;

public class CurrencyTabCompleter implements TabCompleter {
    private final EzEconomyPlugin plugin;

    public CurrencyTabCompleter(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.currency") || sender.hasPermission("ezeconomy.currency"))) return Collections.emptyList();
        if (args.length == 1) {
            // subcommands: set, convert, view
            List<String> subs = List.of("set", "convert", "view");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        // /currency set <currency>
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                return cfg.getConfigurationSection("multi-currency.currencies").getKeys(false).stream()
                        .filter(k -> k.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        // /currency convert <from> <to> <amount>
        if (args.length == 2 && args[0].equalsIgnoreCase("convert")) {
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                return cfg.getConfigurationSection("multi-currency.currencies").getKeys(false).stream()
                        .filter(k -> k.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("convert")) {
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                return cfg.getConfigurationSection("multi-currency.currencies").getKeys(false).stream()
                        .filter(k -> k.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}