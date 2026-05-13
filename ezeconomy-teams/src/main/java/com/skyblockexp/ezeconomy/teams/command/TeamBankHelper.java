package com.skyblockexp.ezeconomy.teams.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.service.BankEconomyService;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import com.skyblockexp.teamsapi.api.TeamsAPI;
import com.skyblockexp.teamsapi.model.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Shared utilities for /teambank subcommands.
 *
 * <p>Provides team resolution and bank-key generation so each subcommand does not
 * duplicate lookup logic.</p>
 */
final class TeamBankHelper {

    /** Prefix used for all team bank keys in EzEconomy storage. */
    static final String BANK_KEY_PREFIX = "team:";

    private TeamBankHelper() {}

    /**
     * Returns the storage key for a team's bank.
     *
     * @param team the team
     * @return the bank key (e.g. {@code "team:550e8400-e29b-41d4-a716-446655440000"})
     */
    static String bankKey(Team team) {
        return BANK_KEY_PREFIX + team.getId().toString();
    }

    /**
     * Resolves the team the player belongs to, sending a {@code no_team} message
     * if the player has no team.
     *
     * @param sender  the command sender (must be a {@link Player})
     * @param plugin  the EzEconomy plugin (used for messages)
     * @return an {@link Optional} containing the team, or empty if the player
     *         has no team or TeamsAPI is not available
     */
    static Optional<Team> resolveTeam(CommandSender sender, EzEconomyPlugin plugin) {
        if (!(sender instanceof Player player)) {
            MessageUtils.send(sender, plugin, "only_players");
            return Optional.empty();
        }

        if (!TeamsAPI.isAvailable()) {
            MessageUtils.send(sender, plugin, "no_team");
            return Optional.empty();
        }

        Optional<Team> team = TeamsAPI.getService().getPlayerTeam(player.getUniqueId());
        if (team.isEmpty()) {
            MessageUtils.send(sender, plugin, "no_team");
        }
        return team;
    }

    /**
     * Ensures a team bank exists in EzEconomy storage, creating it on first use.
     *
     * @param bankService the bank service
     * @param bankKey     the bank storage key
     * @param team        the team (used to obtain the owner UUID)
     */
    static void ensureBankExists(BankEconomyService bankService, String bankKey, Team team) {
        if (!bankService.bankExists(bankKey)) {
            bankService.createBank(bankKey, team.getOwnerUUID());
        }
    }
}
