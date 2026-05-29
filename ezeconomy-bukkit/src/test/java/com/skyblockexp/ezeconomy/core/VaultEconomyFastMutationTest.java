package com.skyblockexp.ezeconomy.core;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultEconomyFastMutationTest {
    private EzEconomyPlugin plugin;
    private RecordingStorage storage;
    private VaultEconomyImpl vault;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(EzEconomyPlugin.class);
        storage = new RecordingStorage();
        TestSupport.injectField(plugin, "storage", storage);
        plugin.getConfig().set("performance.fast-vault-mutations.enabled", true);
        plugin.getConfig().set("performance.balance-cache.enabled", true);
        plugin.getConfig().set("caching-strategy", "LOCAL");
        plugin.setLockManager(null);
        vault = new VaultEconomyImpl(plugin);
    }

    @AfterEach
    void tearDown() {
        if (vault != null) {
            vault.shutdown();
        }
        MockBukkit.unmock();
    }

    @Test
    void depositPlayer_localFastMutation_returnsCachedBalanceAndFlushesFinalBalance() {
        UUID uuid = UUID.randomUUID();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        storage.setBalance(uuid, "dollar", 10.0);
        storage.resetCounts();

        EconomyResponse response = vault.depositPlayer(player, 5.0);

        assertEquals(EconomyResponse.ResponseType.SUCCESS, response.type);
        assertEquals(15.0, response.balance, 0.0001);
        assertEquals(15.0, vault.getBalance(player), 0.0001);

        vault.shutdown();
        assertEquals(15.0, storage.getBalance(uuid, "dollar"), 0.0001);
        assertEquals(0, storage.depositAndGetBalanceCalls);
        assertEquals(1, storage.setBalanceCalls);
    }

    @Test
    void withdrawPlayer_localFastMutation_rejectsInsufficientFundsWithoutFlush() {
        UUID uuid = UUID.randomUUID();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        storage.setBalance(uuid, "dollar", 3.0);
        storage.resetCounts();

        EconomyResponse response = vault.withdrawPlayer(player, 5.0);

        assertEquals(EconomyResponse.ResponseType.FAILURE, response.type);
        assertEquals(3.0, response.balance, 0.0001);

        vault.shutdown();
        assertEquals(3.0, storage.getBalance(uuid, "dollar"), 0.0001);
        assertEquals(0, storage.withdrawAndGetBalanceCalls);
        assertEquals(0, storage.setBalanceCalls);
    }

    private static final class RecordingStorage extends TestSupport.MockStorage {
        private int setBalanceCalls;
        private int depositAndGetBalanceCalls;
        private int withdrawAndGetBalanceCalls;

        @Override
        public void setBalance(UUID uuid, String currency, double amount) {
            setBalanceCalls++;
            super.setBalance(uuid, currency, amount);
        }

        @Override
        public EconomyMutationResult depositAndGetBalance(UUID uuid, String currency, double amount) {
            depositAndGetBalanceCalls++;
            super.deposit(uuid, currency, amount);
            return EconomyMutationResult.success(super.getBalance(uuid, currency));
        }

        @Override
        public EconomyMutationResult withdrawAndGetBalance(UUID uuid, String currency, double amount) {
            withdrawAndGetBalanceCalls++;
            boolean success = super.tryWithdraw(uuid, currency, amount);
            double balance = super.getBalance(uuid, currency);
            return success
                    ? EconomyMutationResult.success(balance)
                    : EconomyMutationResult.failure(balance, "Insufficient funds");
        }

        private void resetCounts() {
            setBalanceCalls = 0;
            depositAndGetBalanceCalls = 0;
            withdrawAndGetBalanceCalls = 0;
        }
    }
}
