package com.skyblockexp.ezeconomy.papi.formatting;

import com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BalanceCurrencyVariantsTest {

    @AfterEach
    public void tearDown() {
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }

    @Test
    public void balance_formatted_and_short_with_currency_suffix_are_handled() throws Exception {
        MockBukkit.mock();
        com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin papi = (com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin.class);

        TestEzEconomyStubs.SimpleStorageProvider sp = new TestEzEconomyStubs.SimpleStorageProvider();
        UUID u = UUID.randomUUID();
        sp.setBalance(u, "gold", 2500.5);

        TestEzEconomyStubs.SimpleTestEz stub = new TestEzEconomyStubs.SimpleTestEz(sp, "dollar") {
            @Override public String format(double amount, String currency) { return String.format(java.util.Locale.US, "FMT:%.1f:%s", amount, currency); }
            @Override public String formatShort(double amount, String currency) { return String.format(java.util.Locale.US, "SRT:%.1f:%s", amount, currency); }
        };

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(papi);

        org.bukkit.OfflinePlayer fake = TestPlayerFakes.fakeOfflinePlayer(u);

        String formatted = expansion.onPlaceholderRequest(fake, "balance_formatted_gold");
        assertNotNull(formatted);
        assertTrue(formatted.contains("gold") || formatted.contains("FMT:"));

        String shorted = expansion.onPlaceholderRequest(fake, "balance_short_gold");
        assertNotNull(shorted);
        assertTrue(shorted.contains("gold") || shorted.contains("SRT:"));
    }
}
