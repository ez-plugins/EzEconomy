package com.skyblockexp.ezeconomy.tabcomplete;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.*;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.PlayerLookup;

public class PayTabCompleter implements TabCompleter {
    private final EzEconomyPlugin plugin;
    private static final int MAX_NAME_SUGGESTIONS = 50;
    private static final List<String> AMOUNT_HINTS = Arrays.asList("1k", "2k", "5k", "10k", "100", "1000", "1m");
    private static final List<String> PAY_HINTS = Arrays.asList("100", "1000", "10000");

    public PayTabCompleter(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ezeconomy.pay")) return Collections.emptyList();
        // For /payall the first argument is the amount, not a player name.
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if (command != null && (command.getName().equalsIgnoreCase("payall") || alias.equalsIgnoreCase("payall"))) {
                List<String> out = new ArrayList<>();
                for (String s : AMOUNT_HINTS) {
                    if (s.startsWith(partial)) out.add(s);
                }
                return out;
            }

            LinkedHashSet<String> merged = new LinkedHashSet<>();
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name != null && name.toLowerCase().startsWith(partial)) {
                    merged.add(name);
                    if (merged.size() >= MAX_NAME_SUGGESTIONS) break;
                }
            }

            if (merged.size() < MAX_NAME_SUGGESTIONS) {
                List<String> offline = PlayerLookup.namesStartingWith(partial, MAX_NAME_SUGGESTIONS);
                for (String name : offline) {
                    merged.add(name);
                    if (merged.size() >= MAX_NAME_SUGGESTIONS) break;
                }
            }

            return new ArrayList<>(merged);
        }
        if (args.length == 2) {
            List<String> out = new ArrayList<>();
            for (String s : PAY_HINTS) {
                if (s.startsWith(args[1])) out.add(s);
            }
            return out;
        }
        if (args.length == 3) {
            var cfg = plugin.getConfig();
            if (cfg.isConfigurationSection("multi-currency.currencies")) {
                String partial = args[2].toLowerCase();
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
