package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import com.skyblockexp.ezeconomy.service.PaymentExecutor;
import com.skyblockexp.ezeconomy.util.PlayerLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayCommandOfflineSequentialFeatureTest {
    private Object server;
    private EzEconomyPlugin plugin;
    private TestSupport.MockStorage storage;
    private static final String CURRENCY = "dollar";

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
    void multipleSequentialPaymentsToSameOfflineRecipientAccumulateCorrectly() throws Exception {
        Object offlineObj;
        try {
            java.lang.reflect.Method addOffline = server.getClass().getMethod("addOfflinePlayer", String.class);
            offlineObj = addOffline.invoke(server, "offline_chain_mod");
        } catch (NoSuchMethodException ignored) {
            offlineObj = org.bukkit.Bukkit.getOfflinePlayer("offline_chain_mod");
        }
        org.bukkit.OfflinePlayer offline = (org.bukkit.OfflinePlayer) offlineObj;
        PlayerLookup.addToCache(offline);

        storage.setBalance(offline.getUniqueId(), CURRENCY, 0.0);

        Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "sender_chain_mod");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
        sender.setOp(true);
        storage.setBalance(sender.getUniqueId(), CURRENCY, 10_000.0);

        PaymentExecutor.execute(plugin, sender, "offline_chain_mod", java.math.BigDecimal.valueOf(1000.0), CURRENCY, true);
        PaymentExecutor.execute(plugin, sender, "offline_chain_mod", java.math.BigDecimal.valueOf(2000.0), CURRENCY, true);
        PaymentExecutor.execute(plugin, sender, "offline_chain_mod", java.math.BigDecimal.valueOf(3000.0), CURRENCY, true);

        assertEquals(4_000.0, storage.getBalance(sender.getUniqueId(), CURRENCY), 0.0001);
        assertEquals(6_000.0, storage.getBalance(offline.getUniqueId(), CURRENCY), 0.0001);
    }
}
