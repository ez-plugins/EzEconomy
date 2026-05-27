package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutorEventCancelTest {

    private Object server;

    @BeforeEach
    void setup() throws Exception {
        try { server = MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); server = MockBukkit.mock(); }
    }

    @AfterEach
    void teardown() { try { MockBukkit.unmock(); } catch (Exception ignored) {} }

    @Test
    void cancelledEvent_abortsBeforeWithdraw() throws Exception {
        Object pFrom = server.getClass().getMethod("addPlayer", String.class).invoke(server, "alice");
        Object pTo = server.getClass().getMethod("addPlayer", String.class).invoke(server, "bob");
        PlayerMock from = (PlayerMock) pFrom;
        PlayerMock to = (PlayerMock) pTo;

        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try {
            // Register a listener that cancels the PlayerPayPlayerEvent
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPay(com.skyblockexp.ezeconomy.api.events.PlayerPayPlayerEvent e) {
                    e.setCancelled(true);
                    e.setCancelReason("nope");
                }
            }, plugin);

            // install a storage that will flag tryWithdraw if called
            final java.util.concurrent.atomic.AtomicBoolean withdrawCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
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
                @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { withdrawCalled.set(true); return false; }
                @Override public void deposit(UUID uuid, String currency, double amount) {}
                @Override public java.util.Map<UUID, Double> getAllBalances(String currency) { return java.util.Collections.emptyMap(); }
                @Override public java.util.List<com.skyblockexp.ezeconomy.api.storage.models.Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }
                @Override public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) { return com.skyblockexp.ezeconomy.storage.TransferResult.success(0,0); }
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

            // run payment; should return true but not call withdraw
            boolean res = PaymentExecutor.execute(plugin, from, "bob", BigDecimal.valueOf(5.0), "dollar");
            assertTrue(res);
            assertFalse(withdrawCalled.get(), "tryWithdraw should not be called when event is cancelled");
            // verify player was sent the cancel reason directly
            // PlayerMock stores messages; check last message contains reason
            String last = from.nextMessage();
            assertNotNull(last);
            assertTrue(last.contains("nope") || last.equals("nope"));
        } finally {
            try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
        }
    }
}
