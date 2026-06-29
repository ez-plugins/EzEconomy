package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySQLStorageProviderTransferConsistencyTest {

    private static final class TestMySQLStorageProvider extends MySQLStorageProvider {
        private final Map<String, Double> balances = new HashMap<String, Double>();
        private UUID creditFailureTarget;

        private TestMySQLStorageProvider(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
            super(plugin, dbConfig);
        }

        private String key(UUID uuid, String currency) {
            return uuid + ":" + currency;
        }

        @Override
        public double getBalance(UUID uuid, String currency) {
            return balances.getOrDefault(key(uuid, currency), 0.0);
        }

        @Override
        public EconomyMutationResult withdrawAndGetBalance(UUID uuid, String currency, double amount) {
            double current = getBalance(uuid, currency);
            if (current < amount) {
                return EconomyMutationResult.failure(current, "Insufficient funds");
            }
            double updated = current - amount;
            balances.put(key(uuid, currency), updated);
            return EconomyMutationResult.success(updated);
        }

        @Override
        public EconomyMutationResult depositAndGetBalance(UUID uuid, String currency, double amount) {
            if (creditFailureTarget != null && creditFailureTarget.equals(uuid)) {
                return EconomyMutationResult.failure(getBalance(uuid, currency), "Storage failure");
            }
            double updated = getBalance(uuid, currency) + amount;
            balances.put(key(uuid, currency), updated);
            return EconomyMutationResult.success(updated);
        }

        @Override
        public void setBalance(UUID uuid, String currency, double amount) {
            balances.put(key(uuid, currency), amount);
        }
    }

    private TestMySQLStorageProvider newProvider() {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        YamlConfiguration runtimeConfig = new YamlConfiguration();
        YamlConfiguration dbConfig = new YamlConfiguration();
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);

        when(plugin.getConfig()).thenReturn(runtimeConfig);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLockManager()).thenReturn(null);
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(pluginManager);

        return new TestMySQLStorageProvider(plugin, dbConfig);
    }

    @Test
    void transfer_creditFailure_rollsBackSenderAndKeepsReceiverStable() {
        TestMySQLStorageProvider provider = newProvider();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        provider.setBalance(from, "dollar", 100.0);
        provider.setBalance(to, "dollar", 50.0);
        provider.creditFailureTarget = to;

        TransferResult result = provider.transfer(from, to, "dollar", 20.0, 20.0);

        assertFalse(result.isSuccess());
        assertEquals(100.0, provider.getBalance(from, "dollar"), 0.0001);
        assertEquals(50.0, provider.getBalance(to, "dollar"), 0.0001);
    }

    @Test
    void transfer_success_updatesBothBalancesAtomicallyFromCallerView() {
        TestMySQLStorageProvider provider = newProvider();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        provider.setBalance(from, "dollar", 100.0);
        provider.setBalance(to, "dollar", 50.0);

        TransferResult result = provider.transfer(from, to, "dollar", 20.0, 20.0);

        assertTrue(result.isSuccess());
        assertEquals(80.0, provider.getBalance(from, "dollar"), 0.0001);
        assertEquals(70.0, provider.getBalance(to, "dollar"), 0.0001);
        assertEquals(80.0, result.getFromBalance(), 0.0001);
        assertEquals(70.0, result.getToBalance(), 0.0001);
    }
}
