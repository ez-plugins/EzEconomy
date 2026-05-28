package com.skyblockexp.ezeconomy.papi.formatting;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BalanceFormattedShortEzFormatterTest {

    @AfterEach
    void cleanup() {
        com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
    }

    @Test
    public void ezPlugin_customFormatter_format_paths_are_called() throws Exception {
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
        MockBukkit.mock();
        com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin papi = null;
        com.skyblockexp.ezeconomy.core.EzEconomyPlugin core = null;

        try {
            papi = (com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.papi.EzEconomyPapiPlugin.class);
            core = (com.skyblockexp.ezeconomy.core.EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.papi.EzPluginPathCoverageTest.SimpleEz.class);

            com.skyblockexp.ezeconomy.api.storage.StorageProvider sp = new com.skyblockexp.ezeconomy.papi.EzPluginPathCoverageTest.TestStorage();
            UUID u = UUID.randomUUID();
            ((com.skyblockexp.ezeconomy.papi.EzPluginPathCoverageTest.TestStorage) sp).put(u, 321.0);
            core.setStorage(sp);

            com.skyblockexp.ezeconomy.service.format.CurrencyFormatter custom = new com.skyblockexp.ezeconomy.service.format.CurrencyFormatter(core) {
                @Override public String format(double amount, String currency) { return "FMT:" + amount + ":" + currency; }
                @Override public String formatPriceForMessage(double amount, String currency) { return "PRICE:" + amount + ":" + currency; }
                @Override public String formatShort(double amount, String currency) { return "SHRT:" + amount + ":" + currency; }
            };
            Field cf = null;
            Class<?> ccls = core.getClass();
            while (ccls != null) {
                try { cf = ccls.getDeclaredField("currencyFormatter"); break; } catch (NoSuchFieldException e) { ccls = ccls.getSuperclass(); }
            }
            if (cf == null) throw new RuntimeException("currencyFormatter field not found");
            cf.setAccessible(true);
            cf.set(core, custom);

            final com.skyblockexp.ezeconomy.core.EzEconomyPlugin coreFinal = core;
            final com.skyblockexp.ezeconomy.service.format.CurrencyFormatter customFinal = custom;

            java.lang.reflect.Field[] fields = org.bukkit.Bukkit.getPluginManager().getClass().getDeclaredFields();
            for (Field f : fields) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object map = f.get(org.bukkit.Bukkit.getPluginManager());
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, org.bukkit.plugin.Plugin> m = (java.util.Map<String, org.bukkit.plugin.Plugin>) map;
                        m.put("EzEconomy", core);
                    } catch (ClassCastException ignored) {}
                }
            }

            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TestEzEconomy() {
                @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() { return coreFinal.getStorageOrWarn(); }
                @Override public String getDefaultCurrency() { return coreFinal.getDefaultCurrency(); }
                @Override public String format(double amount, String currency) { return customFinal.format(amount, currency); }
                @Override public String formatShort(double amount, String currency) { return customFinal.formatShort(amount, currency); }
                @Override public String getCurrencySymbol(String currency) { try { return coreFinal.getCurrencyFormatter().getCurrencySymbol(currency); } catch (Throwable t) { return currency; } }
                @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return coreFinal.getCurrencyPreferenceManager(); }
            };

            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion expansion = new com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion(papi);

            OfflinePlayer fake = com.skyblockexp.ezeconomy.papi.testhelpers.TestPlayerFakes.fakeOfflinePlayer(u);

            String formatted = expansion.onPlaceholderRequest(fake, "balance_formatted_eur");
            assertNotNull(formatted);
            assertTrue(formatted.startsWith("PRICE:") || formatted.startsWith("FMT:"));

            String shorted = expansion.onPlaceholderRequest(fake, "balance_short_eur");
            assertNotNull(shorted);
            assertTrue(shorted.startsWith("SHRT:") || shorted.startsWith("FMT:"));
        } finally {
            com.skyblockexp.ezeconomy.papi.EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = null;
            try { MockBukkit.unmock(); } catch (Exception ignored) {}
        }
    }
}
