package com.skyblockexp.ezeconomy.tabcomplete;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.*;
import java.util.stream.Collectors;

public class PayTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ezeconomy.pay")) return Collections.emptyList();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Arrays.asList("100", "1000", "10000").stream()
                .filter(s -> s.startsWith(args[1]))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}