
package com.skyblockexp.ezeconomy.command;
import com.skyblockexp.ezeconomy.core.MessageProvider;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.*;
import java.util.stream.Collectors;

public class BaltopCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;
    private static final int DEFAULT_TOP = 10;
    private static final int PAGE_SIZE = 10;

    public BaltopCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int top = DEFAULT_TOP;
        boolean usePaging = false;
        int page = 1;
        if (args.length == 2 && args[0].equalsIgnoreCase("page")) {
            usePaging = true;
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        } else if (args.length == 1) {
            try {
                top = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        com.skyblockexp.ezeconomy.api.storage.StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            sender.sendMessage("§cStorage provider unavailable. Check server logs.");
            return true;
        }
        Map<UUID, Double> balances = storage.getAllBalances(plugin.getDefaultCurrency());
        List<Map.Entry<UUID, Double>> sorted = balances.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        MessageProvider messages = plugin.getMessageProvider();
        if (usePaging) {
            int totalEntries = sorted.size();
            int totalPages = (int) Math.ceil(totalEntries / (double) PAGE_SIZE);
            if (totalPages == 0) {
                totalPages = 1;
            }
            if (page <= 0 || page > totalPages) {
                sender.sendMessage(messages.get("baltop_invalid_page", java.util.Map.of(
                        "page", String.valueOf(page),
                        "total_pages", String.valueOf(totalPages),
                        "page_size", String.valueOf(PAGE_SIZE)
                )));
                return true;
            }
            sender.sendMessage(messages.get("top_balances_page", java.util.Map.of(
                    "page", String.valueOf(page),
                    "total_pages", String.valueOf(totalPages),
                    "page_size", String.valueOf(PAGE_SIZE)
            )));
            int startIndex = (page - 1) * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, totalEntries);
            sorted = sorted.subList(startIndex, endIndex);
            int rank = startIndex + 1;
            for (Map.Entry<UUID, Double> entry : sorted) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                sender.sendMessage(messages.get("rank_balance", java.util.Map.of(
                    "rank", String.valueOf(rank),
                    "player", player.getName(),
                    "balance", plugin.getEconomy().format(entry.getValue())
                )));
                rank++;
            }
            return true;
        }
        if (top > 0 && sorted.size() > top) {
            sorted = sorted.subList(0, top);
        }
        sender.sendMessage(messages.get("top_balances", java.util.Map.of("top", String.valueOf(top))));
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : sorted) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            sender.sendMessage(messages.get("rank_balance", java.util.Map.of(
                "rank", String.valueOf(rank),
                "player", player.getName(),
                "balance", plugin.getEconomy().format(entry.getValue())
            )));
            rank++;
        }
        return true;
    }
}
