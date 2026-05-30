package com.skyblockexp.ezeconomy.tabcomplete;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.PlayerLookup;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class BalanceTabCompleter implements TabCompleter {
    private final EzEconomyPlugin plugin;
    private static final int MAX_NAME_SUGGESTIONS = 50;

    public BalanceTabCompleter(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender.hasPermission("ezeconomy.user.balance") || sender.hasPermission("ezeconomy.balance"))) return Collections.emptyList();
        // /balance [player|currency] [currency]
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> res = new ArrayList<>();
            boolean canLookupOthers = sender.hasPermission("ezeconomy.user.balance.others") || sender.hasPermission("ezeconomy.balance.others");
            if (canLookupOthers) {
                LinkedHashSet<String> names = new LinkedHashSet<>();
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    String name = p.getName();
                    if (name != null && name.toLowerCase().startsWith(partial)) {
                        names.add(name);
                        if (names.size() >= MAX_NAME_SUGGESTIONS) break;
                    }
                }
                if (names.size() < MAX_NAME_SUGGESTIONS) {
                    for (String name : PlayerLookup.namesStartingWith(partial, MAX_NAME_SUGGESTIONS)) {
                        names.add(name);
                        if (names.size() >= MAX_NAME_SUGGESTIONS) break;
                    }
                }
                res.addAll(names);
            }

            // suggest currencies
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                for (String k : cfg.getConfigurationSection("multi-currency.currencies").getKeys(false)) {
                    if (k.toLowerCase().startsWith(partial)) res.add(k);
                }
            }
            return res;
        }
        if (args.length == 2) {
            if (!(sender.hasPermission("ezeconomy.user.balance.others") || sender.hasPermission("ezeconomy.balance.others"))) return Collections.emptyList();
            String partial = args[1].toLowerCase();
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                List<String> out = new ArrayList<>();
                for (String k : cfg.getConfigurationSection("multi-currency.currencies").getKeys(false)) {
                    if (k.toLowerCase().startsWith(partial)) out.add(k);
                }
                return out;
            }
        }
        return Collections.emptyList();
    }
}
