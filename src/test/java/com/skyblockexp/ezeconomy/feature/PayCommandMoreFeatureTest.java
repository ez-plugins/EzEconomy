package com.skyblockexp.ezeconomy.feature;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.PlayerLookup;

import java.lang.reflect.Field;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PayCommandMoreFeatureTest {
    // avoid declaring ServerMock as a field to prevent classloading issues during test discovery
    private Object server;
    private EzEconomyPlugin plugin;
    private TestSupport.MockStorage storage;
    private static final String CURRENCY = "dollar";

    @BeforeEach
    public void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(EzEconomyPlugin.class);

        storage = new TestSupport.MockStorage();
        TestSupport.injectField(plugin, "storage", storage);

        plugin.loadMessageProvider();
    }

    @AfterEach
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void testPayCommand_nonexistentRecipient_noChanges() {
        try {
            Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payer2");
            org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
            sender.setOp(true);

            storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);

            sender.performCommand("pay nobody 5");

            // balances unchanged because recipient not found
            assertEquals(10.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPayCommand_insufficientFunds_noTransfer() {
        try {
            Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "poor");
            Object recipientObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "rich");
            org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
            org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) recipientObj;
            sender.setOp(true);

            storage.setBalance(sender.getUniqueId(), CURRENCY, 1.0);
            storage.setBalance(recipient.getUniqueId(), CURRENCY, 0.0);

            sender.performCommand("pay rich 5");

            assertEquals(1.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
            assertEquals(0.0, storage.getBalance(recipient.getUniqueId(), CURRENCY), 0.0001);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPayCommand_cannotPaySelf_noTransfer() {
        try {
            Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "selfy");
            org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
            sender.setOp(true);

            storage.setBalance(sender.getUniqueId(), CURRENCY, 20.0);

            sender.performCommand("pay selfy 5");

            assertEquals(20.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPayCommand_offlineRecipient_asyncTransfer() throws Exception {
        // Create an offline player record for 'offlinebob'
        Object offlineObj = null;
        try {
            // If ServerMock exposes addOfflinePlayer, use it to ensure hasPlayedBefore is true
            java.lang.reflect.Method addOffline = server.getClass().getMethod("addOfflinePlayer", String.class);
            offlineObj = addOffline.invoke(server, "offlinebob");
        } catch (NoSuchMethodException ignored) {
            // fallback
            offlineObj = org.bukkit.Bukkit.getOfflinePlayer("offlinebob");
        }

        org.bukkit.OfflinePlayer offline = (org.bukkit.OfflinePlayer) offlineObj;

        // Ensure recipient has a zero balance initially
        storage.setBalance(offline.getUniqueId(), CURRENCY, 0.0);

        // Create sender online and give funds
        Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "senderOnline");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
        sender.setOp(true);
        storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);

        // Perform the command which will use the async offline path
        sender.performCommand("pay offlinebob 5");

        // Wait briefly for async task to complete
        Thread.sleep(300);

        // Assert balances updated
        assertEquals(5.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(5.0, storage.getBalance(offline.getUniqueId(), CURRENCY), 0.0001);
    }

    @Test
    public void testPayCommand_multipleSequentialPaymentsToSameOfflineRecipient() throws Exception {
        Object offlineObj;
        try {
            java.lang.reflect.Method addOffline = server.getClass().getMethod("addOfflinePlayer", String.class);
            offlineObj = addOffline.invoke(server, "offline_chain");
        } catch (NoSuchMethodException ignored) {
            offlineObj = org.bukkit.Bukkit.getOfflinePlayer("offline_chain");
        }
        org.bukkit.OfflinePlayer offline = (org.bukkit.OfflinePlayer) offlineObj;
        PlayerLookup.addToCache(offline);

        storage.setBalance(offline.getUniqueId(), CURRENCY, 0.0);

        Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "sender_chain");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
        sender.setOp(true);
        storage.setBalance(sender.getUniqueId(), CURRENCY, 10_000.0);

        sender.performCommand("pay offline_chain 1000");
        sender.performCommand("pay offline_chain 2000");
        sender.performCommand("pay offline_chain 3000");

        waitForBalance(sender.getUniqueId(), 4_000.0, 2_000L);
        waitForBalance(offline.getUniqueId(), 6_000.0, 2_000L);

        assertEquals(4_000.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(6_000.0, storage.getBalance(offline.getUniqueId(), CURRENCY), 0.0001);
    }

    private void waitForBalance(java.util.UUID uuid, double expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Math.abs(storage.getBalance(uuid, CURRENCY) - expected) < 0.0001) {
                return;
            }
            Thread.sleep(25L);
        }
    }
}
