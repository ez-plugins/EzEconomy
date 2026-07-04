package com.skyblockexp.ezeconomy.papi.symbols;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SymbolFallbackAndThrowingTest {

    @Test
    public void expansion_uses_fallback_symbol_when_ez_throws() {
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy orig = com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS;
        try {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
                @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return new com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs.SimpleStorageProvider(); }
                @Override public String getDefaultCurrency() { return "dollar"; }
                @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "%.2f %s", amount, currency); }
                @Override public String formatShort(double amount, String currency) { return format(amount, currency); }
                @Override public String getCurrencySymbol(String currency) { throw new RuntimeException("no symbol"); }
                @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
            };

            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);
            assertEquals("$", expansion.onPlaceholderRequest(null, "symbol_dollar"));
        } finally {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = orig;
        }
    }
}


