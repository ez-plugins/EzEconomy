package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutorCurrencyPreferenceTest {

    private Object server;

    @BeforeEach
    void setup() throws Exception {
        try { server = MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); server = MockBukkit.mock(); }
    }

    @AfterEach
    void teardown() { try { MockBukkit.unmock(); } catch (Exception ignored) {} }

    @Test
    void currencyPreference_fallbackLocalLock_depositsConvertedAmount() throws Exception {
        Object pFrom = server.getClass().getMethod("addPlayer", String.class).invoke(server, "alice");
        Object pTo = server.getClass().getMethod("addPlayer", String.class).invoke(server, "bob");
        PlayerMock from = (PlayerMock) pFrom;
        PlayerMock to = (PlayerMock) pTo;

        // Load real plugin via MockBukkit so Bukkit APIs work
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            // configure conversion in plugin config
            plugin.getConfig().set("multi-currency.conversion.dollar.euro", "2.0");
            plugin.getConfig().set("multi-currency.currencies.dollar.decimals", 2);
            plugin.getConfig().set("multi-currency.currencies.euro.decimals", 2);

            // simple in-test storage provider that records deposits
            final java.util.concurrent.atomic.AtomicReference<java.util.Map.Entry<java.util.UUID, java.util.Map.Entry<String, Double>>> lastDeposit = new java.util.concurrent.atomic.AtomicReference<>();
            StorageProvider storage = new StorageProvider() {
                @Override public void init() {}
                @Override public void load() {}
                @Override public void save() {}
                @Override public boolean isConnected() { return true; }
                @Override public double getBalance(java.util.UUID uuid, String currency) { return uuid.equals(from.getUniqueId()) ? 100.0 : 0.0; }
                @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(java.util.UUID uuid) { return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, uuid.toString(), uuid.toString()); }
                @Override public boolean playerExists(java.util.UUID uuid) { return true; }
                @Override public void setBalance(java.util.UUID uuid, String currency, double amount) {}
                @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
                @Override public boolean tryWithdraw(java.util.UUID uuid, String currency, double amount) { return uuid.equals(from.getUniqueId()) && amount <= 100.0; }
                @Override public void deposit(java.util.UUID uuid, String currency, double amount) { lastDeposit.set(new java.util.AbstractMap.SimpleEntry<>(uuid, new java.util.AbstractMap.SimpleEntry<>(currency, amount))); }
                @Override public java.util.Map<java.util.UUID, Double> getAllBalances(String currency) { return java.util.Collections.emptyMap(); }
                @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(java.util.UUID uuid, String currency) { return java.util.Collections.emptyList(); }
                @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(java.util.UUID fromUuid, java.util.UUID toUuid, String currency, double debitAmount, double creditAmount) { return com.skyblockexp.ezeconomy.storage.TransferResult.success(0,0); }
                @Override public void shutdown() {}
                @Override public boolean createBank(String name, java.util.UUID owner) { return false; }
                @Override public boolean deleteBank(String name) { return false; }
                @Override public boolean bankExists(String name) { return false; }
                @Override public double getBankBalance(String name, String currency) { return 0; }
                @Override public void setBankBalance(String name, String currency, double amount) {}
                @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
                @Override public void depositBank(String name, String currency, double amount) {}
                @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
                @Override public boolean isBankOwner(String name, java.util.UUID uuid) { return false; }
                @Override public boolean isBankMember(String name, java.util.UUID uuid) { return false; }
                @Override public boolean addBankMember(String name, java.util.UUID uuid) { return false; }
                @Override public boolean removeBankMember(String name, java.util.UUID uuid) { return false; }
                @Override public java.util.Set<java.util.UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
            };

            plugin.setStorage(storage);

            // currency preference: set bob to prefer euro
            com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager pref = new com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager(plugin) {
                @Override public String getPreferredCurrency(java.util.UUID uuid) { return uuid.equals(to.getUniqueId()) ? "euro" : super.getPreferredCurrency(uuid); }
            };
            plugin.setCurrencyPreferenceManager(pref);

            // ensure currency manager default
            plugin.setCurrencyManager(new com.skyblockexp.ezeconomy.manager.CurrencyManager(plugin));

            // install a basic MessageProvider to avoid NPEs
            plugin.setMessageProvider(new com.skyblockexp.ezeconomy.core.MessageProvider(new org.bukkit.configuration.file.YamlConfiguration(), new org.bukkit.configuration.file.YamlConfiguration(), "en"));

            // run payment
            boolean res = PaymentExecutor.execute(plugin, from, "bob", BigDecimal.valueOf(10.0), "dollar");
            assertTrue(res);

            // inspect lastDeposit variable
            java.util.Map.Entry<java.util.UUID, java.util.Map.Entry<String, Double>> d = lastDeposit.get();
            assertNotNull(d, "Expected a deposit to have occurred");
            assertEquals(to.getUniqueId(), d.getKey());
            assertEquals("euro", d.getValue().getKey());
            assertEquals(20.0, d.getValue().getValue(), 0.0001);
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }
}
