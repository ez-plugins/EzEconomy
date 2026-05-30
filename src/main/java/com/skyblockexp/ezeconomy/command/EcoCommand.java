package com.skyblockexp.ezeconomy.command;


import com.skyblockexp.ezeconomy.command.eco.GiveSubcommand;
import com.skyblockexp.ezeconomy.command.eco.GuiSubcommand;
import com.skyblockexp.ezeconomy.command.eco.SetSubcommand;
import com.skyblockexp.ezeconomy.command.eco.TakeSubcommand;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.MessageUtils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.ConsoleCommandSender;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EcoCommand implements CommandExecutor {
    private final EzEconomyPlugin plugin;
    private final Map<String, Subcommand> subcommands;

    public EcoCommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.subcommands = new HashMap<>();
        this.subcommands.put("gui", new GuiSubcommand(plugin));
        this.subcommands.put("give", new GiveSubcommand(plugin));
        this.subcommands.put("take", new TakeSubcommand(plugin));
        this.subcommands.put("set", new SetSubcommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Optionally open the user GUI when /eco is used with no args
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (plugin.getUserGuiConfig().getBoolean("open-on-eco", false)) {
                    // Require explicit gui permission for opening the user GUI
                    if (!(sender instanceof ConsoleCommandSender) && !(player.hasPermission("ezeconomy.user.eco.gui") || player.hasPermission("ezeconomy.eco.gui"))) {
                        MessageUtils.send(sender, plugin, "no_permission");
                        return true;
                    }
                    com.skyblockexp.ezeconomy.gui.MainGui.open(plugin, player);
                    return true;
                }
            }
            MessageUtils.send(sender, plugin, "usage_eco");
            return true;
        }

        String subcommandKey = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        Subcommand subcommand = subcommands.get(subcommandKey);
        if (subcommand != null) {
            return subcommand.execute(sender, subArgs);
        }

        // Unknown subcommand
        MessageUtils.send(sender, plugin, "unknown_action");
        return true;
    }
}
