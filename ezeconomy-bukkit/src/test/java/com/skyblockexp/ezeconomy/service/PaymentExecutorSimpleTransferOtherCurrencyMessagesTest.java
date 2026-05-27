package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutorSimpleTransferOtherCurrencyMessagesTest {

    private Object server;

    @BeforeEach
    void setup() throws Exception {
        try { server = MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); server = MockBukkit.mock(); }
    }

    @AfterEach
    void teardown() { try { MockBukkit.unmock(); } catch (Exception ignored) {} }

    @Test
    void transferSuccess_sendsPaidOtherCurrencyAndReceivedOtherCurrency() throws Exception {
        Object pFrom = server.getClass().getMethod("addPlayer", String.class).invoke(server, "alice");
        Object pTo = server.getClass().getMethod("addPlayer", String.class).invoke(server, "bob");
        PlayerMock from = (PlayerMock) pFrom;
        PlayerMock to = (PlayerMock) pTo;

        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            // set multi-currency default to euro so currency != default
            plugin.getConfig().set("multi-currency.enabled", true);
            plugin.getConfig().set("multi-currency.default", "euro");
            plugin.getConfig().set("multi-currency.conversion.dollar.euro", "2.0");
            plugin.getConfig().set("multi-currency.currencies.dollar.decimals", 2);
            plugin.getConfig().set("multi-currency.currencies.euro.decimals", 2);

            StorageProvider storage = new StorageProvider() {
                @Override public void init() {}
                @Override public void load() {}
                @Override public void save() {}
                @Override public boolean isConnected() { return true; }
                @Override public double getBalance(UUID uuid, String currency) { return 100.0; }
                @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, uuid.toString(), uuid.toString()); }
                @Override public boolean playerExists(UUID uuid) { return true; }
                @Override public void setBalance(UUID uuid, String currency, double amount) {}
                @Override public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction transaction) {}
                @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return true; }
                @Override public void deposit(UUID uuid, String currency, double amount) {}
                @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return java.util.Collections.emptyMap(); }
                @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
                @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) { return com.skyblockexp.ezeconomy.storage.TransferResult.success(90.0, 10.0); }
                @Override public void shutdown() {}
                @Override public boolean createBank(String name, UUID owner) { return false; }
                @Override public boolean deleteBank(String name) { return false; }
                @Override public boolean bankExists(String name) { return false; }
                @Override public double getBankBalance(String name, String currency) { return 0; }
                @Override public void setBankBalance(String name, String currency, double amount) {}
                @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
                @Override public void depositBank(String name, String currency, double amount) {}
                @Override public java.util.Set<UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
                @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
                @Override public boolean isBankMember(String name, UUID uuid) { return false; }
                @Override public boolean addBankMember(String name, UUID uuid) { return false; }
                @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
                @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
            };
            plugin.setStorage(storage);

            boolean res = PaymentExecutor.execute(plugin, from, "bob", java.math.BigDecimal.valueOf(10.0), "dollar");
            assertTrue(res);

            String payerMsg = from.nextMessage();
            String receiverMsg = to.nextMessage();
            assertNotNull(payerMsg, "Expected payer to receive a message");
            assertNotNull(receiverMsg, "Expected receiver to receive a message");
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }
}
