package com.skyblockexp.ezeconomy.teams.command;

import org.bukkit.command.CommandSender;

/**
 * Interface for /teambank subcommands.
 */
public interface TeamBankSubcommand {
    /**
     * Executes the subcommand.
     *
     * @param sender  the command sender
     * @param args    the arguments (not including the subcommand name itself)
     * @return true if the command was handled
     */
    boolean execute(CommandSender sender, String[] args);
}
