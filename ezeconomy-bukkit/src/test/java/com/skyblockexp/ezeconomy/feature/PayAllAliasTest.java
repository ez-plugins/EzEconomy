package com.skyblockexp.ezeconomy.feature;

import org.mockbukkit.mockbukkit.MockBukkit;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PayAllAliasTest {
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
    public void testPayAllAlias_behavesLikePayStar() {
        try {
            Object s1 = server.getClass().getMethod("addPlayer", String.class).invoke(server, "aliasPayer");
            Object r1 = server.getClass().getMethod("addPlayer", String.class).invoke(server, "aliceA");
            Object r2 = server.getClass().getMethod("addPlayer", String.class).invoke(server, "bobB");
            org.bukkit.entity.Player payer = (org.bukkit.entity.Player) s1;
            org.bukkit.entity.Player alice = (org.bukkit.entity.Player) r1;
            org.bukkit.entity.Player bob = (org.bukkit.entity.Player) r2;

            // Give necessary permissions (but do not grant bypass-withdraw)
            payer.setOp(false);
            payer.addAttachment(plugin, "ezeconomy.payall", true);
            payer.addAttachment(plugin, "ezeconomy.pay", true);

            storage.setBalance(payer.getUniqueId(), CURRENCY, 20.0);
            storage.setBalance(alice.getUniqueId(), CURRENCY, 0.0);
            storage.setBalance(bob.getUniqueId(), CURRENCY, 0.0);

            // Use the alias command
            payer.performCommand("payall 10");

            // payer should have 0, recipients 10 each
            assertEquals(0.0, storage.getBalance(payer.getUniqueId(), CURRENCY), 0.0001);
            assertEquals(10.0, storage.getBalance(alice.getUniqueId(), CURRENCY), 0.0001);
            assertEquals(10.0, storage.getBalance(bob.getUniqueId(), CURRENCY), 0.0001);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
