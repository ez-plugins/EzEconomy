package com.skyblockexp.ezeconomy.papi.symbols;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SymbolDollarDefaultTest {

    @Test
    public void default_symbol_for_dollar_is_dollar_sign() {
        org.mockbukkit.mockbukkit.MockBukkit.mock();
        try {
            com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin papi = (com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin) org.mockbukkit.mockbukkit.MockBukkit.load(com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin.class);
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy orig = com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS;
            try {
                com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
                    private final com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs.SimpleStorageProvider sp = new com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyStubs.SimpleStorageProvider();
                    @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return sp; }
                    @Override public String getDefaultCurrency() { return "dollar"; }
                    @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "%.2f %s", amount, currency); }
                    @Override public String formatShort(double amount, String currency) { return format(amount, currency); }
                    @Override public String getCurrencySymbol(String currency) { return "$"; }
                    @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
                };

                com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(papi);
                String out = expansion.onPlaceholderRequest(null, "symbol_dollar");
                assertEquals("$", out);
            } finally {
                com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = orig;
            }
        } finally {
            try { org.mockbukkit.mockbukkit.MockBukkit.unmock(); } catch (Exception ignored) {}
        }
    }
}


