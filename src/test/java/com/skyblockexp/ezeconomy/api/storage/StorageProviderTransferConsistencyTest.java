package com.skyblockexp.ezeconomy.api.storage;

import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import com.skyblockexp.ezeconomy.dto.EconomyPlayer;
import com.skyblockexp.ezeconomy.storage.TransferResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageProviderTransferConsistencyTest {

    private static final class FakeProvider implements StorageProvider {
        private final Map<String, Double> balances = new HashMap<String, Double>();
        private UUID failCreditForUuid;

        private String key(UUID uuid, String currency) {
            return uuid + ":" + currency;
        }

        @Override
        public EconomyMutationResult depositAndGetBalance(UUID uuid, String currency, double amount) {
            if (failCreditForUuid != null && failCreditForUuid.equals(uuid)) {
                return EconomyMutationResult.failure(getBalance(uuid, currency), "Storage failure");
            }
            double updated = getBalance(uuid, currency) + amount;
            setBalance(uuid, currency, updated);
            return EconomyMutationResult.success(updated);
        }

        @Override
        public EconomyMutationResult withdrawAndGetBalance(UUID uuid, String currency, double amount) {
            double current = getBalance(uuid, currency);
            if (current < amount) {
                return EconomyMutationResult.failure(current, "Insufficient funds");
            }
            double updated = current - amount;
            setBalance(uuid, currency, updated);
            return EconomyMutationResult.success(updated);
        }

        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return balances.getOrDefault(key(uuid, currency), 0.0); }
        @Override public void setBalance(UUID uuid, String currency, double amount) { balances.put(key(uuid, currency), amount); }
        @Override public void logTransaction(Transaction transaction) {}
        @Override public List<Transaction> getTransactions(UUID uuid, String currency) { return Collections.emptyList(); }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return false; }
        @Override public void deposit(UUID uuid, String currency, double amount) {}
        @Override public Map<UUID, Double> getAllBalances(String currency) { return Collections.emptyMap(); }
        @Override public boolean isConnected() { return true; }
        @Override public void shutdown() {}
        @Override public EconomyPlayer getPlayer(UUID uuid) { return null; }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 0; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public Set<String> getBanks() { return Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public Set<UUID> getBankMembers(String name) { return Collections.emptySet(); }
    }

    @Test
    void transfer_creditFailure_rollsBackSenderAndPreventsDesync() {
        FakeProvider provider = new FakeProvider();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        provider.setBalance(from, "dollar", 100.0);
        provider.setBalance(to, "dollar", 50.0);
        provider.failCreditForUuid = to;

        TransferResult result = provider.transfer(from, to, "dollar", 20.0, 20.0);

        assertFalse(result.isSuccess());
        assertEquals(100.0, provider.getBalance(from, "dollar"), 0.0001);
        assertEquals(50.0, provider.getBalance(to, "dollar"), 0.0001);
    }

    @Test
    void transfer_success_updatesBothSidesConsistently() {
        FakeProvider provider = new FakeProvider();
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
