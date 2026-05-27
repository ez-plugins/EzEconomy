package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayBalanceConsistencyFeatureTest {
    private Object server;
    private EzEconomyPlugin plugin;
    private TestSupport.MockStorage storage;
    private static final String CUR = "dollar";

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(EzEconomyPlugin.class);
        storage = new TestSupport.MockStorage();
        TestSupport.injectField(plugin, "storage", storage);
        plugin.loadMessageProvider();
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    @Test
    void payAndBalanceOthersStayConsistentAndLogsReportCorrectPostBalances() throws Exception {
        Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "payer_consistency");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
        sender.setOp(true);
        sender.addAttachment(plugin, "ezeconomy.pay", true);
        sender.addAttachment(plugin, "ezeconomy.balance", true);
        sender.addAttachment(plugin, "ezeconomy.balance.others", true);

        Object recipientObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "recipient_consistency");
        org.bukkit.entity.Player recipient = (org.bukkit.entity.Player) recipientObj;

        storage.setBalance(sender.getUniqueId(), CUR, 5000.0);
        storage.setBalance(recipient.getUniqueId(), CUR, 0.0);

        List<String> logs = new ArrayList<>();
        Handler h = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record != null && record.getMessage() != null) logs.add(record.getMessage());
            }
            @Override
            public void flush() {}
            @Override
            public void close() {}
        };
        plugin.getLogger().addHandler(h);
        try {
            sender.performCommand("pay recipient_consistency 1000");

            assertEquals(4000.0, storage.getBalance(sender.getUniqueId(), CUR), 0.0001);
            assertEquals(1000.0, storage.getBalance(recipient.getUniqueId(), CUR), 0.0001);

            sender.performCommand("balance recipient_consistency");
            String balanceMsg = ((PlayerMock) sender).nextMessage();
            String expected = plugin.getCurrencyFormatter().formatPriceForMessage(1000.0, plugin.getDefaultCurrency());
            assertTrue(balanceMsg.contains("recipient_consistency"));
            assertTrue(balanceMsg.contains(expected), "Expected /balance output to contain " + expected + " but was: " + balanceMsg);

            boolean hasTransferLog = logs.stream().anyMatch(m ->
                    m.contains("PaymentExecutor: transfer result success=true")
                            && m.contains("debit=1000.0 " + CUR)
                            && m.contains("fromBalancePost=4000.0")
                            && m.contains("toBalancePost=1000.0"));
            assertTrue(hasTransferLog, "Expected transfer result log with debit and post balances");
        } finally {
            plugin.getLogger().removeHandler(h);
        }
    }
}
