package com.skyblockexp.ezeconomy.teams.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.service.BankEconomyService;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import com.skyblockexp.teamsapi.model.Team;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.Optional;

/**
 * Subcommand: /teambank balance
 *
 * <p>Displays the balance of the sender's team bank in the default currency.</p>
 */
public class BalanceSubcommand implements TeamBankSubcommand {

    private final EzEconomyPlugin plugin;

    public BalanceSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ezeconomy.teambank.balance")
                && !sender.hasPermission("ezeconomy.teambank.admin")) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }

        Optional<Team> teamOpt = TeamBankHelper.resolveTeam(sender, plugin);
        if (teamOpt.isEmpty()) {
            return true;
        }
        Team team = teamOpt.get();

        var storage = plugin.getStorageOrWarn();
        if (storage == null) {
            MessageUtils.send(sender, plugin, "storage_unavailable");
            return true;
        }
        BankEconomyService bankService = new BankEconomyService(storage);

        String bankKey = TeamBankHelper.bankKey(team);
        TeamBankHelper.ensureBankExists(bankService, bankKey, team);

        String currency = plugin.getDefaultCurrency();
        double balance = bankService.getBankBalance(bankKey, currency);
        String formattedBalance = plugin.getCurrencyFormatter().formatPriceForMessage(balance, currency);

        MessageUtils.send(sender, plugin, "teambank_balance", Map.of(
                "team", team.getDisplayName(),
                "balance", formattedBalance,
                "currency", currency
        ));
        return true;
    }
}
