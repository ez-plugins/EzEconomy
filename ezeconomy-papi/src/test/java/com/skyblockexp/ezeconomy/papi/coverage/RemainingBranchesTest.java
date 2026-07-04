package com.skyblockexp.ezeconomy.papi.coverage;

import com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs;
import org.bukkit.OfflinePlayer;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RemainingBranchesTest {

    @AfterEach
    public void tearDown() {
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
    }

    @Test
    public void balance_formatted_with_null_storage_returns_zero_formatted() {
        // TestEz with null storage should return formatted zero for balance_formatted
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new TestEzEconomyStubs.SimpleTestEz(null, "dollar");

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        OfflinePlayer p = offlinePlayer(UUID.randomUUID());

        String v1 = expansion.onPlaceholderRequest(p, "balance_formatted");
            assertEquals("0.00 dollar", v1);

        String v2 = expansion.onPlaceholderRequest(p, "balance_formatted_dollar");
            assertEquals("0.00 dollar", v2);
    }

    @Test
    public void balance_short_with_null_storage_returns_short_format_of_zero() {
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new TestEzEconomyStubs.SimpleTestEz(null, "dollar");
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        OfflinePlayer p = offlinePlayer(UUID.randomUUID());

        String s1 = expansion.onPlaceholderRequest(p, "balance_short");
            assertEquals("0.00 dollar", s1);

        String s2 = expansion.onPlaceholderRequest(p, "balance_short_dollar");
            assertEquals("0.00 dollar", s2);
    }

    @Test
    public void balance_uses_default_currency_when_preference_manager_is_null() {
        TestEzEconomyStubs.SimpleStorageProvider sp = new TestEzEconomyStubs.SimpleStorageProvider();
        UUID id = UUID.randomUUID();
        sp.setBalance(id, "dollar", 12.34);

        // SimpleTestEz has a null CurrencyPreferenceManager by default
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new TestEzEconomyStubs.SimpleTestEz(sp, "dollar");

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

        OfflinePlayer p = offlinePlayer(id);

        String out = expansion.onPlaceholderRequest(p, "balance");
        assertTrue(out.contains("12.34"));
    }

    private OfflinePlayer offlinePlayer(UUID id) {
        return TestPlayerFakes.fakeOfflinePlayer(id);
    }
}
