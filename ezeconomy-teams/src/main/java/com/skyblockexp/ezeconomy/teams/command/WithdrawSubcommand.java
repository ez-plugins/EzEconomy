package com.skyblockexp.ezeconomy.teams.command;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.service.BankEconomyService;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import com.skyblockexp.ezeconomy.util.NumberUtil;
import com.skyblockexp.teamsapi.model.Team;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

/**
 * Subcommand: /teambank withdraw &lt;amount&gt; [currency]
 *
 * <p>Withdraws the given amount from the team bank and deposits it into the
 * sender's personal balance.</p>
 *
 * <p>When {@code banking.team-bank-withdraw-owner-only} is {@code true} in
 * {@code config.yml}, only the team owner may withdraw. Members with the
 * {@code ezeconomy.teambank.admin} permission bypass this restriction.</p>
 */
public class WithdrawSubcommand implements TeamBankSubcommand {

    private final EzEconomyPlugin plugin;

    public WithdrawSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ezeconomy.teambank.withdraw")
                && !sender.hasPermission("ezeconomy.teambank.admin")) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtils.send(sender, plugin, "usage_teambank_withdraw");
            return true;
        }

        Optional<Team> teamOpt = TeamBankHelper.resolveTeam(sender, plugin);
        if (teamOpt.isEmpty()) {
            return true;
        }
        Team team = teamOpt.get();

        if (plugin.getStorageOrWarn() == null) {
            MessageUtils.send(sender, plugin, "storage_unavailable");
            return true;
        }

        Player player = (Player) sender;

        // Owner-only guard (admin permission bypasses)
        boolean ownerOnly = plugin.getConfig().getBoolean("banking.team-bank-withdraw-owner-only", false);
        if (ownerOnly && !player.hasPermission("ezeconomy.teambank.admin")
                && !team.isOwner(player.getUniqueId())) {
            MessageUtils.send(sender, plugin, "teambank_no_permission_withdraw");
            return true;
        }

        String amountStr = args[0];
        String currency = args.length >= 2 ? args[1] : plugin.getDefaultCurrency();

        Double amount = NumberUtil.parseDouble(amountStr);
        if (amount == null || amount <= 0) {
            MessageUtils.send(sender, plugin, "invalid_amount");
            return true;
        }

        BankEconomyService bankService = new BankEconomyService(plugin.getStorageOrWarn());
        String bankKey = TeamBankHelper.bankKey(team);
        TeamBankHelper.ensureBankExists(bankService, bankKey, team);

        if (!bankService.tryWithdrawBank(bankKey, currency, amount)) {
            MessageUtils.send(sender, plugin, "teambank_insufficient_funds");
            return true;
        }

        // Credit the player's personal balance
        plugin.getEconomy().depositPlayer(player, amount, currency);

        String formattedAmount = plugin.getCurrencyFormatter().formatPriceForMessage(amount, currency);
        MessageUtils.send(sender, plugin, "teambank_withdraw_success", Map.of(
                "team", team.getDisplayName(),
                "amount", formattedAmount,
                "currency", currency
        ));
        return true;
    }
}
