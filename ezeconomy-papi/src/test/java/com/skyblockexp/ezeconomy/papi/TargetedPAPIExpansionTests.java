package com.skyblockexp.ezeconomy.papi;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TargetedPAPIExpansionTests {

    @Test
    public void top_withEmptyStorage_returnsLoading_thenEmpty() {
        EzEconomyPAPIExpansion expansion = new EzEconomyPAPIExpansion(null);

        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = new EzEconomyPAPIExpansion.TestEzEconomy() {
            @Override public com.skyblockexp.ezeconomy.api.storage.StorageProvider getStorageOrWarn() {
                return new com.skyblockexp.ezeconomy.api.storage.StorageProvider() {
                    @Override public void init() {}
                    @Override public void load() {}
                    @Override public void save() {}
                    @Override public double getBalance(UUID uuid, String currency) { return 0; }
                    @Override public void setBalance(UUID uuid, String currency, double amount) {}
                    @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
                    @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return Collections.emptyList(); }
                    @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return false; }
                    @Override public void deposit(UUID uuid, String currency, double amount) {}
                    @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return Collections.emptyMap(); }
                    @Override public void shutdown() {}
                    @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return null; }
                    @Override public boolean createBank(String name, UUID owner) { return false; }
                    @Override public boolean deleteBank(String name) { return false; }
                    @Override public boolean bankExists(String name) { return false; }
                    @Override public double getBankBalance(String name, String currency) { return 0; }
                    @Override public void setBankBalance(String name, String currency, double amount) {}
                    @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
                    @Override public void depositBank(String name, String currency, double amount) {}
                    @Override public java.util.Set<String> getBanks() { return Collections.emptySet(); }
                    @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
                    @Override public boolean isBankMember(String name, UUID uuid) { return false; }
                    @Override public boolean addBankMember(String name, UUID uuid) { return false; }
                    @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
                    @Override public java.util.Set<UUID> getBankMembers(String name) { return Collections.emptySet(); }
                    @Override public java.util.Set<String> cleanupOrphanedPlayers() { return Collections.emptySet(); }
                    @Override public boolean isConnected() { return true; }
                    @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) { return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0,0); }
                };
            }

            @Override public String getDefaultCurrency() { return "dollar"; }
            @Override public String format(double amount, String currency) { return String.format(Locale.ROOT, "$%.2f", amount); }
            @Override public String formatShort(double amount, String currency) { return String.format(Locale.ROOT, "$%.0f", amount); }
            @Override public String getCurrencySymbol(String currency) { return "$"; }
            @Override public com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager getCurrencyPreferenceManager() { return null; }
        };

        String first = expansion.onPlaceholderRequest(null, "top_3_dollar");
        assertEquals("loading", first);

        String second = expansion.onPlaceholderRequest(null, "top_3_dollar");
        // After refresh with empty results the cache stores empty string
        assertEquals("", second);
    }

    @Test
    public void bank_withNullStorage_returnsEmpty() {
        EzEconomyPAPIExpansion expansion = new EzEconomyPAPIExpansion(null);
        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyHelpers.formatting("dollar", "$");

        String out = expansion.onPlaceholderRequest(null, "bank_test_dollar");
        assertEquals("", out);
    }

    @Test
    public void symbol_emptyString_returnsDollarFallback() {
        EzEconomyPAPIExpansion expansion = new EzEconomyPAPIExpansion(null);
        EzEconomyPAPIExpansion.TEST_ECONOMY_FOR_TESTS = com.skyblockexp.ezeconomy.papi.testhelpers.TestEzEconomyHelpers.emptyFormatting("dollar");

        String out = expansion.onPlaceholderRequest(null, "symbol_dollar");
        assertEquals("$", out);
    }
}


