package com.skyblockexp.ezeconomy.papi.symbols;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

public class SymbolFallbackTest extends com.skyblockexp.ezeconomy.papi.TestBase {

    @Test
    public void symbol_fallback_to_default_when_unset() {
        MockBukkit.mock();
        String result = null;
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy orig = com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS;
        try {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
                @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return new com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs.SimpleStorageProvider(); }
                @Override public String getDefaultCurrency() { return "dollar"; }
                @Override public String format(double amount, String currency) { return String.format(java.util.Locale.US, "%.2f %s", amount, currency); }
                @Override public String formatShort(double amount, String currency) { return format(amount, currency); }
                @Override public String getCurrencySymbol(String currency) { return "$"; }
                @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
            };

            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);

                result = expansion.onPlaceholderRequest(null, "symbol_dollar");
        } finally {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = orig;
        }
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
