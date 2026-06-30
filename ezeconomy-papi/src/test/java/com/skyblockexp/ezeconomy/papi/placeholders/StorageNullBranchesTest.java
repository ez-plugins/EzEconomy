package com.skyblockexp.ezeconomy.papi.placeholders;

import com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs;
import com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StorageNullBranchesTest {

    @Test
    public void storage_null_paths_return_expected_defaults() {
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy orig = com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS;
        try {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
                @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return null; }
                @Override public String getDefaultCurrency() { return "dollar"; }
                @Override public String format(double amount, String currency) { return String.format(java.util.Locale.US, "%.2f %s", amount, currency); }
                @Override public String formatShort(double amount, String currency) { return format(amount, currency); }
                @Override public String getCurrencySymbol(String currency) { return null; }
                @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
            };

            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

            java.util.UUID u = java.util.UUID.randomUUID();
            org.bukkit.OfflinePlayer fakePlayer = TestPlayerFakes.fakeOfflinePlayer(u);

            assertEquals("0.00 dollar", expansion.onPlaceholderRequest(fakePlayer, "balance"));
            assertEquals("0.00 eur", expansion.onPlaceholderRequest(fakePlayer, "balance_eur"));
            assertEquals("0.00 formatted", expansion.onPlaceholderRequest(fakePlayer, "balance_formatted"));
            assertEquals("0.00 formatted_eur", expansion.onPlaceholderRequest(fakePlayer, "balance_formatted_eur"));
            assertEquals("0.00 short", expansion.onPlaceholderRequest(fakePlayer, "balance_short"));
            assertEquals("0.00 short_eur", expansion.onPlaceholderRequest(fakePlayer, "balance_short_eur"));

            assertEquals("", expansion.onPlaceholderRequest(fakePlayer, "top_10"));

            String topResult = expansion.onPlaceholderRequest(fakePlayer, "top_5_dollar");
            assertEquals("loading", topResult);

            assertEquals("", expansion.onPlaceholderRequest(fakePlayer, "bank_main_dollar"));

        } finally {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = orig;
        }
    }
}
