package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutorDistributedLockInsufficientFundsTest {

    private Object server;

    @BeforeEach
    void setup() throws Exception {
        try { server = MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); server = MockBukkit.mock(); }
    }

    @AfterEach
    void teardown() { try { MockBukkit.unmock(); } catch (Exception ignored) {} }

    @Test
    void distributedLockPath_insufficientFunds_reportsAndReleasesLock() throws Exception {
        Object pFrom = server.getClass().getMethod("addPlayer", String.class).invoke(server, "alice");
        Object pTo = server.getClass().getMethod("addPlayer", String.class).invoke(server, "bob");
        PlayerMock from = (PlayerMock) pFrom;
        PlayerMock to = (PlayerMock) pTo;

        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            // configure conversion in plugin config
            plugin.getConfig().set("multi-currency.conversion.dollar.euro", "2.0");
            plugin.getConfig().set("multi-currency.currencies.dollar.decimals", 2);
            plugin.getConfig().set("multi-currency.currencies.euro.decimals", 2);

            final AtomicReference<java.util.Map.Entry<UUID, java.util.Map.Entry<String, Double>>> lastDeposit = new AtomicReference<>();

            StorageProvider storage = new StorageProvider() {
                @Override public void init() {}
                @Override public void load() {}
                @Override public void save() {}
                @Override public boolean isConnected() { return true; }
                @Override public double getBalance(UUID uuid, String currency) { return uuid.equals(from.getUniqueId()) ? 5.0 : 0.0; }
                @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, uuid.toString(), uuid.toString()); }
                @Override public boolean playerExists(UUID uuid) { return true; }
                @Override public void setBalance(UUID uuid, String currency, double amount) {}
                @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
                @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return false; }
                @Override public void deposit(UUID uuid, String currency, double amount) { lastDeposit.set(new java.util.AbstractMap.SimpleEntry<>(uuid, new java.util.AbstractMap.SimpleEntry<>(currency, amount))); }
                @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return java.util.Collections.emptyMap(); }
                @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
                @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) { return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0,0); }
                @Override public void shutdown() {}
                @Override public boolean createBank(String name, UUID owner) { return false; }
                @Override public boolean deleteBank(String name) { return false; }
                @Override public boolean bankExists(String name) { return false; }
                @Override public double getBankBalance(String name, String currency) { return 0; }
                @Override public void setBankBalance(String name, String currency, double amount) {}
                @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
                @Override public void depositBank(String name, String currency, double amount) {}
                @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
                @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
                @Override public boolean isBankMember(String name, UUID uuid) { return false; }
                @Override public boolean addBankMember(String name, UUID uuid) { return false; }
                @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
                @Override public java.util.Set<UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
            };

            plugin.setStorage(storage);

            // currency preference: set bob to prefer euro
            com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager pref = new com.skyblockexp.ezeconomy.manager.CurrencyPreferenceManager(plugin) {
                @Override public String getPreferredCurrency(UUID uuid) { return uuid.equals(to.getUniqueId()) ? "euro" : super.getPreferredCurrency(uuid); }
            };
            plugin.setCurrencyPreferenceManager(pref);

            // ensure currency manager default
            plugin.setCurrencyManager(new com.skyblockexp.ezeconomy.manager.CurrencyManager(plugin));

            // install lock manager that simulates distributed locks
            final AtomicBoolean released = new AtomicBoolean(false);
            com.skyblockexp.ezeconomy.lock.LockManager lm = new com.skyblockexp.ezeconomy.lock.LockManager() {
                @Override
                public String acquire(UUID uuid, long ttlMs, long retryMs, int maxAttempts) throws InterruptedException {
                    return "token-" + uuid.toString();
                }

                @Override
                public boolean release(UUID uuid, String token) {
                    released.set(true);
                    return true;
                }

                @Override
                public String[] acquireOrdered(UUID[] uuids, long ttlMs, long retryMs, int maxAttempts) throws InterruptedException {
                    String[] tokens = new String[uuids.length];
                    for (int i = 0; i < uuids.length; i++) tokens[i] = "tok" + i;
                    return tokens;
                }

                @Override
                public void releaseOrdered(UUID[] uuids, String[] tokens) {
                    released.set(true);
                }
            };
            plugin.setLockManager(lm);

            // install a basic MessageProvider to avoid NPEs
            plugin.setMessageProvider(new com.skyblockexp.ezeconomy.core.MessageProvider(new org.bukkit.configuration.file.YamlConfiguration(), new org.bukkit.configuration.file.YamlConfiguration(), "en"));

            // run payment (10 dollars -> requires >5 balance -> insufficient)
            boolean res = PaymentExecutor.execute(plugin, from, "bob", BigDecimal.valueOf(10.0), "dollar");
            assertTrue(res);

            // No deposit should have occurred
            assertNull(lastDeposit.get(), "No deposit should be made when insufficient funds");
            // Lock manager should have been released in finally
            assertTrue(released.get(), "LockManager should have been released");
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }
}
