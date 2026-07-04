package com.skyblockexp.ezeconomy.placeholder;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.service.format.CurrencyFormatter;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EzEconomyPlaceholderExpansionTest {

    @AfterEach
    void tearDown() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {
        }
    }

    @Test
    void explicit_currency_placeholders_use_requested_currency_formatter_and_plain_variant_is_available() throws Exception {
        Object server = MockBukkit.mock();
        EzEconomyPlugin plugin = MockBukkit.load(EzEconomyPlugin.class);
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OfflinePlayer player = (OfflinePlayer) server.getClass().getMethod("addPlayer", String.class).invoke(server, "TestPlayer");

        YamlConfiguration config = new YamlConfiguration();
        config.set("multi-currency.enabled", true);
        config.set("multi-currency.default", "dollar");
        plugin.getConfig().set("multi-currency.enabled", true);
        plugin.getConfig().set("multi-currency.default", "dollar");

        TestSupport.MockStorage storage = new TestSupport.MockStorage();
        storage.setBalance(uuid, "dollar", 15.5);
        storage.setBalance(uuid, "stardust", 42.5);
        TestSupport.injectField(plugin, "storage", storage);
        plugin.setCurrencyPreferenceManager(null);

        CurrencyFormatter formatter = new CurrencyFormatter(plugin) {
            @Override
            public String format(double amount, String currency) {
                if ("dollar".equals(currency)) {
                    return "15.50 $";
                }
                if ("stardust".equals(currency)) {
                    return "42.50 ✧";
                }
                return String.format("%.2f %s", amount, currency);
            }

            @Override
            public String formatAmountOnly(double amount, String currency) {
                if ("stardust".equals(currency)) {
                    return "42.50";
                }
                return String.format("%.2f", amount);
            }
        };
        TestSupport.injectField(plugin, "currencyFormatter", formatter);

        EzEconomyPlaceholderExpansion expansion = new EzEconomyPlaceholderExpansion(plugin);

        assertEquals("15.50 $", expansion.onRequest(player, "balance"));
        assertEquals("42.50 ✧", expansion.onRequest(player, "balance_stardust"));
        assertEquals("42.50", expansion.onRequest(player, "balance_plain_stardust"));
    }
}