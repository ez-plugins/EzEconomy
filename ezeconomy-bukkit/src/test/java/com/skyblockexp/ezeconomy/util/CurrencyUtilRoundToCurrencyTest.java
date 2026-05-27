package com.skyblockexp.ezeconomy.util;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyUtilRoundToCurrencyTest {

    @BeforeEach
    void setup() {
        try { MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); MockBukkit.mock(); }
    }

    @AfterEach
    void teardown() { try { MockBukkit.unmock(); } catch (Exception ignored) {} }

    @Test
    void roundsToConfiguredDecimals() {
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            plugin.getConfig().set("multi-currency.currencies.test.decimals", 3);
            double rounded = com.skyblockexp.ezeconomy.util.CurrencyUtil.roundToCurrency(plugin, 1.23456, "test");
            assertEquals(1.235, rounded, 1e-12);
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }

    @Test
    void roundsToZeroDecimalsWhenConfigured() {
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            plugin.getConfig().set("multi-currency.currencies.tcoin.decimals", 0);
            double rounded = com.skyblockexp.ezeconomy.util.CurrencyUtil.roundToCurrency(plugin, 1.6, "tcoin");
            assertEquals(2.0, rounded, 1e-12);
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }
}
