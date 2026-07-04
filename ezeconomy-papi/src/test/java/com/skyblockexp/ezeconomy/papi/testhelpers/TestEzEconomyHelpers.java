package com.skyblockexp.ezeconomy.papi.testhelpers;

import java.util.Locale;

public final class TestEzEconomyHelpers {

    private TestEzEconomyHelpers() {}

    /**
     * Create a simple TestEzEconomy that uses the provided currency and symbol formatting.
     * StorageProvider is null (tests that use this should not rely on storage).
     */
    public static com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy formatting(final String defaultCurrency, final String symbol) {
        return new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return null; }
            @Override public String getDefaultCurrency() { return defaultCurrency; }
            @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "%s%.2f", symbol, amount); }
            @Override public String formatShort(double amount, String currency) { return String.format(Locale.ROOT, "%s%.0f", symbol, amount); }
            @Override public String getCurrencySymbol(String currency) { return symbol; }
            @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };
    }

    /**
     * Create a TestEzEconomy that returns empty formatted strings and an empty currency symbol.
     * Useful for tests that assert fallback behavior when expansion returns blank values.
     */
    public static com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy emptyFormatting(final String defaultCurrency) {
        return new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return null; }
            @Override public String getDefaultCurrency() { return defaultCurrency; }
            @Override public String format(double amount, String currency) { return ""; }
            @Override public String formatShort(double amount, String currency) { return ""; }
            @Override public String getCurrencySymbol(String currency) { return ""; }
            @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };
    }
}
