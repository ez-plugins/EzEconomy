package com.skyblockexp.ezeconomy.command.eco;

import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import com.skyblockexp.ezeconomy.util.NumberUtil;
import com.skyblockexp.ezeconomy.core.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Map;

/**
 * Subcommand for /eco take <player> <amount>
 */
public class TakeSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public TakeSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !(sender.hasPermission("ezeconomy.user.eco.take") || sender.hasPermission("ezeconomy.eco.take"))) {
            MessageUtils.send(sender, plugin, "no_permission");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            MessageUtils.send(sender, plugin, "usage_eco");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        Money money = NumberUtil.parseMoney(args[1], null);
        if (money == null || money.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            MessageUtils.send(sender, plugin, "invalid_amount", java.util.Map.of("input", args[1]));
            return true;
        }
        double amount = money.getAmount().doubleValue();

        if (args.length == 2) {
            plugin.getEconomy().withdrawPlayer(target, amount);
            MessageUtils.send(sender, plugin, "withdrew", Map.of("name", target.getName(), "amount", plugin.getCurrencyFormatter().formatPriceForMessage(amount, plugin.getDefaultCurrency())));
            return true;
        }

        // args.length == 3 -> currency specified
        String currency = args[2].toLowerCase();
        java.util.Map<String, Object> currencies = plugin.getConfig().getConfigurationSection("multi-currency.currencies") != null
            ? plugin.getConfig().getConfigurationSection("multi-currency.currencies").getValues(false)
            : java.util.Collections.emptyMap();
        if (!currencies.containsKey(currency)) {
            MessageUtils.send(sender, plugin, "unknown_currency", java.util.Map.of("currency", currency));
            return true;
        }

        com.skyblockexp.ezeconomy.api.storage.StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            MessageUtils.send(sender, plugin, "storage_unavailable");
            return true;
        }

        boolean ok = storage.tryWithdraw(target.getUniqueId(), currency, amount);
        if (!ok) {
            MessageUtils.send(sender, plugin, "not_enough_money");
            return true;
        }
        String amountWithSymbol = plugin.getCurrencyFormatter().formatPriceForMessage(amount, currency);
        MessageUtils.send(sender, plugin, "withdrew", Map.of("name", target.getName(), "amount", amountWithSymbol));
        return true;
    }
}