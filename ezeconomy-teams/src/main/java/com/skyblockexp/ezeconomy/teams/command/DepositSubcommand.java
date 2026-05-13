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
 * Subcommand: /teambank deposit &lt;amount&gt; [currency]
 *
 * <p>Withdraws the given amount from the sender's personal balance and
 * deposits it into the team bank.</p>
 */
public class DepositSubcommand implements TeamBankSubcommand {

    private final EzEconomyPlugin plugin;

    public DepositSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ezeconomy.teambank.deposit")
                && !sender.hasPermission("ezeconomy.teambank.admin")) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 1) {
            MessageUtils.send(sender, plugin, "usage_teambank_deposit");
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

        String amountStr = args[0];
        String currency = args.length >= 2 ? args[1] : plugin.getDefaultCurrency();

        Double amount = NumberUtil.parseDouble(amountStr);
        if (amount == null || amount <= 0) {
            MessageUtils.send(sender, plugin, "invalid_amount");
            return true;
        }

        Player player = (Player) sender;
        // Withdraw from personal balance via Vault API
        net.milkbowl.vault.economy.EconomyResponse takeResp =
                plugin.getEconomy().withdrawPlayer(player, amount, currency);
        if (takeResp == null
                || takeResp.type == net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE
                || takeResp.type == net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED) {
            MessageUtils.send(sender, plugin, "not_enough_money");
            return true;
        }

        BankEconomyService bankService = new BankEconomyService(plugin.getStorageOrWarn());
        String bankKey = TeamBankHelper.bankKey(team);
        TeamBankHelper.ensureBankExists(bankService, bankKey, team);
        bankService.depositBank(bankKey, currency, amount);

        String formattedAmount = plugin.getCurrencyFormatter().formatPriceForMessage(amount, currency);
        MessageUtils.send(sender, plugin, "teambank_deposit_success", Map.of(
                "team", team.getDisplayName(),
                "amount", formattedAmount,
                "currency", currency
        ));
        return true;
    }
}
