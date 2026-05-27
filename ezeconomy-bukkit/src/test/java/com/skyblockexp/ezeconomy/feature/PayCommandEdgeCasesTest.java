package com.skyblockexp.ezeconomy.feature;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import com.skyblockexp.ezeconomy.core.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class PayCommandEdgeCasesTest {
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
    public void testConfirmPendingTransferExecutes() throws Exception {
        Object sObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payerC");
        Object rObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payeeC");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) sObj;
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) rObj;
        sender.setOp(true);

        storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);
        storage.setBalance(recipient.getUniqueId(), CURRENCY, 0.0);

        // Create pending transfer via PayFlowManager
        long expires = System.currentTimeMillis() + 60000L;
        plugin.getPayFlowManager().createPendingTransfer(sender.getUniqueId(), recipient.getUniqueId(), "payeeC", Money.of(BigDecimal.valueOf(5), CURRENCY), CURRENCY, expires);

        // Execute confirm
        sender.performCommand("pay confirm");

        assertEquals(5.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(5.0, storage.getBalance(recipient.getUniqueId(), CURRENCY), 0.0001);
    }

    @Test
    public void testInvalidCurrencyArgumentDoesNotTransfer() throws Exception {
        Object sObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payerD");
        Object rObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payeeD");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) sObj;
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) rObj;
        sender.setOp(true);

        storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);

        sender.performCommand("pay payeeD 5 unknowncur");

        assertEquals(10.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(0.0, storage.getBalance(recipient.getUniqueId(), CURRENCY), 0.0001);
    }

    @Test
    public void testInvalidAmountDoesNotTransfer() throws Exception {
        Object sObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payerE");
        Object rObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payeeE");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) sObj;
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) rObj;
        sender.setOp(true);

        storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);

        sender.performCommand("pay payeeE notanumber");

        assertEquals(10.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(0.0, storage.getBalance(recipient.getUniqueId(), CURRENCY), 0.0001);
    }

    @Test
    public void testNegativeAmountIsRejected() throws Exception {
        Object sObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payerF");
        Object rObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payeeF");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) sObj;
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) rObj;
        sender.setOp(true);

        storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);

        sender.performCommand("pay payeeF -5");

        assertEquals(10.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(0.0, storage.getBalance(recipient.getUniqueId(), CURRENCY), 0.0001);
    }

    @Test
    public void testTooManyArgumentsShowsUsageAndNoTransfer() throws Exception {
        Object sObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payerG");
        Object rObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payeeG");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) sObj;
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) rObj;
        sender.setOp(true);

        storage.setBalance(sender.getUniqueId(), CURRENCY, 10.0);

        sender.performCommand("pay a b c d");

        assertEquals(10.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(0.0, storage.getBalance(recipient.getUniqueId(), CURRENCY), 0.0001);
    }

    @Test
    public void testConfirmationThresholdCreatesPendingTransfer() throws Exception {
        // Set threshold low so transfer requires confirmation
        plugin.getConfig().set("pay.confirmation.threshold", 5.0);

        Object sObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payerH");
        Object rObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payeeH");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) sObj;
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) rObj;
        sender.setOp(true);

        storage.setBalance(sender.getUniqueId(), CURRENCY, 100.0);

        sender.performCommand("pay payeeH 10");

        // Payment should be pending confirmation (no balance change yet)
        assertTrue(plugin.getPayFlowManager().isPendingConfirm(sender.getUniqueId()));
        assertEquals(100.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
    }
}
