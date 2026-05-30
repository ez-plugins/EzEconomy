package com.skyblockexp.ezeconomy.tabcomplete;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BaltopTabCompleter implements TabCompleter {
    private final EzEconomyPlugin plugin;

    public BaltopTabCompleter(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.baltop") || sender.hasPermission("ezeconomy.baltop"))) return Collections.emptyList();
        // /baltop [top|page <num>] [currency]
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> res = new ArrayList<>();
            // common presets
            res.addAll(List.of("10", "25", "50", "100").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList()));
            if ("page".startsWith(partial)) res.add("page");
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                res.addAll(cfg.getConfigurationSection("multi-currency.currencies").getKeys(false).stream()
                        .filter(k -> k.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList()));
            }
            return res;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("page")) {
                return List.of("1", "2", "3", "4", "5").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
            String partial = args[1].toLowerCase();
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                return cfg.getConfigurationSection("multi-currency.currencies").getKeys(false).stream()
                        .filter(k -> k.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
