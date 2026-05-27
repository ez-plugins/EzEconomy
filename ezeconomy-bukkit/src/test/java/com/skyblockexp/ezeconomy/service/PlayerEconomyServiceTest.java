package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import com.skyblockexp.ezeconomy.storage.TransferResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerEconomyServiceTest {

    static class FakeStorage implements StorageProvider {
        double balance = 0.0;
        boolean withdrawResult = true;
        List<Transaction> txs = List.of();
        Map<UUID, Double> all = Map.of();
        TransferResult lastTransfer = TransferResult.success(0.0, 0.0);

        @Override public void init() {}
        @Override public void load() {}
        @Override public void save() {}
        @Override public double getBalance(UUID uuid, String currency) { return balance; }
        @Override public void setBalance(UUID uuid, String currency, double amount) { this.balance = amount; }
        @Override public void logTransaction(Transaction transaction) {}
        @Override public List<Transaction> getTransactions(UUID uuid, String currency) { return txs; }
        @Override public boolean tryWithdraw(UUID uuid, String currency, double amount) { return withdrawResult; }
        @Override public void deposit(UUID uuid, String currency, double amount) { this.balance += amount; }
        @Override public Map<UUID, Double> getAllBalances(String currency) { return all; }
        @Override public boolean isConnected() { return true; }
        @Override public void shutdown() {}
        @Override public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) { return null; }
        @Override public boolean createBank(String name, UUID owner) { return false; }
        @Override public boolean deleteBank(String name) { return false; }
        @Override public boolean bankExists(String name) { return false; }
        @Override public double getBankBalance(String name, String currency) { return 0; }
        @Override public void setBankBalance(String name, String currency, double amount) {}
        @Override public boolean tryWithdrawBank(String name, String currency, double amount) { return false; }
        @Override public void depositBank(String name, String currency, double amount) {}
        @Override public java.util.Set<String> getBanks() { return java.util.Collections.emptySet(); }
        @Override public boolean isBankOwner(String name, UUID uuid) { return false; }
        @Override public boolean isBankMember(String name, UUID uuid) { return false; }
        @Override public boolean addBankMember(String name, UUID uuid) { return false; }
        @Override public boolean removeBankMember(String name, UUID uuid) { return false; }
        @Override public java.util.Set<UUID> getBankMembers(String name) { return java.util.Collections.emptySet(); }
        @Override public TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double amount) { return lastTransfer; }
        @Override public TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) { return lastTransfer; }
        @Override public boolean playerExists(UUID uuid) { return true; }
        @Override public java.util.Set<String> cleanupOrphanedPlayers() { return java.util.Collections.emptySet(); }
        @Override public double getBalance(UUID uuid) { return getBalance(uuid, "dollar"); }
        @Override public void setBalance(UUID uuid, double amount) { setBalance(uuid, "dollar", amount); }
        @Override public boolean tryWithdraw(UUID uuid, double amount) { return tryWithdraw(uuid, "dollar", amount); }
        @Override public void deposit(UUID uuid, double amount) { deposit(uuid, "dollar", amount); }
        @Override public Map<UUID, Double> getAllBalances() { return getAllBalances("dollar"); }
    }

    @Test
    void getBalance_delegatesToStorage() {
        FakeStorage fs = new FakeStorage();
        fs.balance = 123.45;
        PlayerEconomyService service = new PlayerEconomyService(fs);
        UUID id = UUID.randomUUID();
        double bal = service.getBalance(id, "dollar");
        assertEquals(123.45, bal, 1e-9);
    }

    @Test
    void deposit_alwaysTrue_andCallsStorage() {
        FakeStorage fs = new FakeStorage();
        PlayerEconomyService service = new PlayerEconomyService(fs);
        UUID id = UUID.randomUUID();
        boolean ok = service.deposit(id, "dollar", 10.0);
        assertTrue(ok);
        assertEquals(10.0, fs.balance, 1e-9);
    }

    @Test
    void withdraw_delegates_tryWithdrawReturn() {
        FakeStorage fs = new FakeStorage();
        fs.withdrawResult = true;
        PlayerEconomyService service = new PlayerEconomyService(fs);
        UUID id = UUID.randomUUID();
        assertTrue(service.withdraw(id, "dollar", 5.0));
        fs.withdrawResult = false;
        assertFalse(service.withdraw(id, "dollar", 6.0));
    }

    @Test
    void getTransactions_andGetAllBalances_delegates() {
        FakeStorage fs = new FakeStorage();
        List<Transaction> txs = List.of();
        fs.txs = txs;
        PlayerEconomyService service = new PlayerEconomyService(fs);
        UUID id = UUID.randomUUID();
        assertSame(txs, service.getTransactions(id, "dollar"));

        Map<UUID, Double> all = Map.of(id, 50.0);
        fs.all = all;
        assertSame(all, service.getAllBalances("dollar"));
    }

    @Test
    void transfer_variants_delegateToStorage() {
        FakeStorage fs = new FakeStorage();
        TransferResult r1 = TransferResult.success(0.0, 100.0);
        fs.lastTransfer = r1;
        PlayerEconomyService service = new PlayerEconomyService(fs);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertSame(r1, service.transfer(a, b, "dollar", 10.0));

        TransferResult r2 = TransferResult.success(0.0, 100.0);
        fs.lastTransfer = r2;
        assertSame(r2, service.transfer(a, b, "dollar", 10.0, 9.0));
    }
}
