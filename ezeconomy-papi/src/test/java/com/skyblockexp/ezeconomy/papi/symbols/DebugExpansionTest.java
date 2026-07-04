package com.skyblockexp.ezeconomy.papi.symbols;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DebugExpansionTest {
    @Test
    public void debugSymbolFallback() {
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy stub = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return null; }
            @Override public String getDefaultCurrency() { return "dollar"; }
            @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "%.2f %s", amount, currency); }
            @Override public String formatShort(double amount, String currency) { return String.format(Locale.ROOT, "%.1f%s", amount >= 1000 ? amount/1000.0 : amount, amount >= 1000 ? "K" : ""); }
            @Override public String getCurrencySymbol(String currency) { return "$"; }
            @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };

        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = stub;
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion exp = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(null);
        String sym = exp.onPlaceholderRequest(null, "symbol_dollar");
        System.out.println("DEBUG harness symbol -> '" + sym + "'");
        org.junit.jupiter.api.Assertions.assertNotNull(sym);
    }
}


