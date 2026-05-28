package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankTransferBalanceFeatureTest {
    private Object server;
    private EzEconomyPlugin plugin;
    private TestSupport.MockStorage storage;

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
    void bankDepositAndWithdrawMovePlayerWalletBalance() throws Exception {
        Object ownerObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "owner_wallet");
        org.bukkit.entity.Player owner = (org.bukkit.entity.Player) ownerObj;
        owner.setOp(true);

        Object memberObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "member_wallet");
        org.bukkit.entity.Player member = (org.bukkit.entity.Player) memberObj;
        member.setOp(true);

        String currency = plugin.getDefaultCurrency();
        storage.setBalance(owner.getUniqueId(), currency, 200.0);
        storage.setBalance(member.getUniqueId(), currency, 0.0);

        assertTrue(owner.performCommand("bank create teamwallet"));
        assertTrue(owner.performCommand("bank deposit teamwallet 100"));
        assertEquals(100.0, storage.getBankBalance("teamwallet", currency), 0.0001);
        assertEquals(100.0, storage.getBalance(owner.getUniqueId(), currency), 0.0001);

        assertTrue(owner.performCommand("bank addmember teamwallet member_wallet"));
        assertTrue(member.performCommand("bank withdraw teamwallet 25"));
        assertEquals(75.0, storage.getBankBalance("teamwallet", currency), 0.0001);
        assertEquals(25.0, storage.getBalance(member.getUniqueId(), currency), 0.0001);
    }
}
