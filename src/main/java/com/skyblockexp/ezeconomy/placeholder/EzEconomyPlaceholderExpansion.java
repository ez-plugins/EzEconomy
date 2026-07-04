package com.skyblockexp.ezeconomy.placeholder;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.service.format.CurrencyFormatter;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class EzEconomyPlaceholderExpansion extends PlaceholderExpansion {

    private EzEconomyPlugin plugin;

    public EzEconomyPlaceholderExpansion(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "Shadow48402";
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "ezeconomy";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String getRequiredPlugin() {
        return "EzEconomy";
    }

    @Override
    public boolean canRegister() {
        return (plugin = (EzEconomyPlugin) Bukkit.getPluginManager().getPlugin(getRequiredPlugin())) != null;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        RequestContext context = createContext(player);
        String[] split = params.toLowerCase().split("_");

        try {
            if (params.equalsIgnoreCase("balance")) {
                return resolveBalance(context, context.preferredCurrency);
            }

            // %ezeconomy_balance_formatted% or %ezeconomy_balance_short%
            if (split.length >= 2 && split[0].equals("balance") && (split[1].equals("formatted") || split[1].equals("short"))) {
                String currency = split.length >= 3 ? split[2] : context.preferredCurrency;
                return "formatted".equals(split[1])
                        ? resolveFormattedBalance(context, currency)
                        : resolveShortBalance(context, currency);
            }

            // %ezeconomy_balance_plain% or %ezeconomy_balance_plain_<currency>%
            if (split.length >= 2 && split[0].equals("balance") && split[1].equals("plain")) {
                String currency = split.length >= 3 ? split[2] : context.preferredCurrency;
                return resolvePlainBalance(context, currency);
            }

            if (split.length == 2 && split[0].equals("balance")) {
                return resolveBalance(context, split[1]);
            }

            if (split.length == 2 && split[0].equals("symbol")) {
                return resolveSymbol(context, split[1]);
            }

            if (split.length >= 2 && split[0].equals("bank")) {
                return resolveBank(context, split);
            }

            if (split.length == 2 && split[0].equals("top")) {
                return resolveTop(context, split[1]);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private RequestContext createContext(OfflinePlayer player) {
        boolean multiEnabled = plugin.getConfig().getBoolean("multi-currency.enabled", false);
        String defaultCurrency = multiEnabled ? plugin.getConfig().getString("multi-currency.default", "dollar") : "dollar";
        String preferredCurrency = defaultCurrency;
        if (player != null && plugin.getCurrencyPreferenceManager() != null) {
            preferredCurrency = plugin.getCurrencyPreferenceManager().getPreferredCurrency(player.getUniqueId());
        }
        if (preferredCurrency == null || preferredCurrency.isBlank()) {
            preferredCurrency = defaultCurrency;
        }
        return new RequestContext(
                plugin,
                plugin.getStorageOrWarn(),
                plugin.getCurrencyFormatter(),
                player,
                multiEnabled,
                preferredCurrency
        );
    }

    private String resolveBalance(RequestContext context, String currency) {
        if (context.player == null || context.storage == null) {
            return null;
        }
        double amount = context.storage.getBalance(context.player.getUniqueId(), currency);
        return context.currencyFormatter.format(amount, currency);
    }

    private String resolveFormattedBalance(RequestContext context, String currency) {
        if (context.player == null || context.storage == null) {
            return null;
        }
        double amount = context.storage.getBalance(context.player.getUniqueId(), currency);
        return context.currencyFormatter.formatPriceForMessage(amount, currency);
    }

    private String resolveShortBalance(RequestContext context, String currency) {
        if (context.player == null || context.storage == null) {
            return null;
        }
        double amount = context.storage.getBalance(context.player.getUniqueId(), currency);
        return context.currencyFormatter.formatShort(amount, currency);
    }

    private String resolvePlainBalance(RequestContext context, String currency) {
        if (context.player == null || context.storage == null) {
            return null;
        }
        double amount = context.storage.getBalance(context.player.getUniqueId(), currency);
        return context.currencyFormatter.formatAmountOnly(amount, currency);
    }

    private String resolveSymbol(RequestContext context, String currency) {
        if (context.multiEnabled && context.plugin.getConfig().contains("multi-currency.currencies." + currency + ".symbol")) {
            return context.plugin.getConfig().getString("multi-currency.currencies." + currency + ".symbol", "$");
        }
        if ("dollar".equals(currency)) {
            return "$";
        }
        return "?";
    }

    private String resolveBank(RequestContext context, String[] split) {
        if (!context.plugin.getConfig().getBoolean("banking.enabled", true)) {
            return null;
        }
        if (context.player == null || context.storage == null) {
            return null;
        }

        String bank = split[1];
        String currency = split.length == 3 ? split[2] : context.preferredCurrency;
        if (!context.storage.isBankMember(bank, context.player.getUniqueId())) {
            return "-";
        }

        double amount = context.storage.getBankBalance(bank, currency);
        return context.currencyFormatter.format(amount, currency);
    }

    private String resolveTop(RequestContext context, String rankText) {
        if (context.storage == null) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(rankText);
        } catch (NumberFormatException e) {
            return null;
        }
        if (rank <= 0) {
            return null;
        }

        java.util.Map<java.util.UUID, Double> balances = context.storage.getAllBalances(context.preferredCurrency);
        java.util.List<java.util.Map.Entry<java.util.UUID, Double>> sorted = balances.entrySet().stream()
                .sorted(java.util.Map.Entry.<java.util.UUID, Double>comparingByValue().reversed())
                .toList();
        if (rank > sorted.size()) {
            return null;
        }

        java.util.Map.Entry<java.util.UUID, Double> entry = sorted.get(rank - 1);
        org.bukkit.OfflinePlayer topPlayer = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey());
        return topPlayer.getName() + ": " + context.currencyFormatter.format(entry.getValue(), context.preferredCurrency);
    }

    private static final class RequestContext {
        private final EzEconomyPlugin plugin;
        private final StorageProvider storage;
        private final CurrencyFormatter currencyFormatter;
        private final OfflinePlayer player;
        private final boolean multiEnabled;
        private final String preferredCurrency;

        private RequestContext(EzEconomyPlugin plugin,
                               StorageProvider storage,
                               CurrencyFormatter currencyFormatter,
                               OfflinePlayer player,
                               boolean multiEnabled,
                               String preferredCurrency) {
            this.plugin = plugin;
            this.storage = storage;
            this.currencyFormatter = currencyFormatter;
            this.player = player;
            this.multiEnabled = multiEnabled;
            this.preferredCurrency = preferredCurrency;
        }
    }
}
