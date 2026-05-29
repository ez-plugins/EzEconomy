package com.skyblockexp.ezeconomy.storage.mysql;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * DAO handling balance-related SQL operations. Uses a Hikari pool when available
 * and falls back to a single connection with striped per-key locking.
 */
public class MySQLBalanceDao {
    private final EzEconomyPlugin plugin;
    private final String table;
    private final HikariDataSource pool;
    private final Connection fallback;
    private final StripedLockManager lockManager;
    private final Function<String, Double> cacheGet;
    private final BiConsumer<String, Double> cachePut;
    private final Supplier<Boolean> canUseLocalFastBalanceResponse;
    private final MySQLBalanceBackgroundPersistenceService balanceBackgroundPersistence;

    public MySQLBalanceDao(EzEconomyPlugin plugin,
                          String table,
                          HikariDataSource pool,
                          Connection fallback,
                          StripedLockManager lockManager,
                          Function<String, Double> cacheGet,
                          BiConsumer<String, Double> cachePut,
                          Supplier<Boolean> canUseLocalFastBalanceResponse,
                          MySQLBalanceBackgroundPersistenceService balanceBackgroundPersistence) {
        this.plugin = plugin;
        this.table = table;
        this.pool = pool;
        this.fallback = fallback;
        this.lockManager = lockManager;
        this.cacheGet = cacheGet;
        this.cachePut = cachePut;
        this.canUseLocalFastBalanceResponse = canUseLocalFastBalanceResponse;
        this.balanceBackgroundPersistence = balanceBackgroundPersistence;
    }

    private String balanceCacheKey(UUID uuid, String currency) {
        return "bal:" + uuid + ":" + currency;
    }

    public EconomyMutationResult depositAndGetBalance(UUID uuid, String currency, double amount) {
        String cacheKey = balanceCacheKey(uuid, currency);
        if (pool != null) {
            String id = BalanceModel.idFor(uuid, currency);
            Double cached = cacheGet.apply(cacheKey);
            // If background persistence is available, enqueue delta and return fast result
            if (balanceBackgroundPersistence != null) {
                balanceBackgroundPersistence.submitBalanceDelta(id, uuid.toString(), currency, amount);
                if (cached != null && canUseLocalFastBalanceResponse.get()) {
                    double pending = balanceBackgroundPersistence.peekPendingSum(id);
                    double updatedFast = cached.doubleValue() + pending;
                    cachePut.accept(cacheKey, updatedFast);
                    return EconomyMutationResult.success(updatedFast);
                }
                // Fallback: read DB then include pending deltas
                try (Connection conn = pool.getConnection();
                     PreparedStatement select = conn.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
                    select.setString(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        double dbBal = rs.next() ? rs.getDouble(1) : 0.0;
                        double pending = balanceBackgroundPersistence.peekPendingSum(id);
                        double updated = dbBal + pending;
                        cachePut.accept(cacheKey, updated);
                        return EconomyMutationResult.success(updated);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("[EzEconomy] MySQL deposit failed (select after enqueue): " + e.getMessage());
                    return EconomyMutationResult.failure(0.0, "Storage failure");
                }
            }

            try (Connection conn = pool.getConnection();
                 PreparedStatement upsert = conn.prepareStatement(
                         "INSERT INTO " + table + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)"
                 );
                 PreparedStatement select = conn.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
                upsert.setString(1, id);
                upsert.setString(2, uuid.toString());
                upsert.setString(3, currency);
                upsert.setDouble(4, amount);
                upsert.executeUpdate();
                if (cached != null && canUseLocalFastBalanceResponse.get()) {
                    double updatedFast = cached.doubleValue() + amount;
                    cachePut.accept(cacheKey, updatedFast);
                    return EconomyMutationResult.success(updatedFast);
                }
                select.setString(1, id);
                try (ResultSet rs = select.executeQuery()) {
                    double updated = rs.next() ? rs.getDouble(1) : amount;
                    cachePut.accept(cacheKey, updated);
                    return EconomyMutationResult.success(updated);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL deposit failed: " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }

        String id = BalanceModel.idFor(uuid, currency);
        Object keyLock = lockManager.lockFor(id);
        synchronized (keyLock) {
            Double cached = cacheGet.apply(cacheKey);
            String upsertSql = "INSERT INTO " + table + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)";
            String selectSql = "SELECT balance FROM " + table + " WHERE id = ?";
            try {
                if (fallback == null || fallback.isClosed()) {
                    plugin.getLogger().severe("[EzEconomy] MySQL connection unavailable for fallback upsert");
                    return EconomyMutationResult.failure(0.0, "Storage failure");
                }
                try (PreparedStatement upsert = fallback.prepareStatement(upsertSql);
                     PreparedStatement select = fallback.prepareStatement(selectSql)) {
                    upsert.setString(1, id);
                    upsert.setString(2, uuid.toString());
                    upsert.setString(3, currency);
                    upsert.setDouble(4, amount);
                    upsert.executeUpdate();
                    if (cached != null && canUseLocalFastBalanceResponse.get()) {
                        double updatedFast = cached.doubleValue() + amount;
                        cachePut.accept(cacheKey, updatedFast);
                        return EconomyMutationResult.success(updatedFast);
                    }
                    select.setString(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        double updated = rs.next() ? rs.getDouble(1) : amount;
                        cachePut.accept(cacheKey, updated);
                        return EconomyMutationResult.success(updated);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL deposit failed (fallback): " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    public EconomyMutationResult withdrawAndGetBalance(UUID uuid, String currency, double amount) {
        String cacheKey = balanceCacheKey(uuid, currency);
        if (pool != null) {
            String id = BalanceModel.idFor(uuid, currency);
            // Ensure any pending deltas are flushed before attempting withdraw
            if (balanceBackgroundPersistence != null) {
                try {
                    balanceBackgroundPersistence.flushIdSync(id);
                } catch (Throwable ignored) {}
            }
            Double cached = cacheGet.apply(cacheKey);
            try (Connection conn = pool.getConnection();
                 PreparedStatement withdraw = conn.prepareStatement(
                         "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?"
                 );
                 PreparedStatement select = conn.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
                withdraw.setDouble(1, amount);
                withdraw.setString(2, id);
                withdraw.setDouble(3, amount);
                int rows = withdraw.executeUpdate();
                if (rows > 0 && cached != null && canUseLocalFastBalanceResponse.get()) {
                    double updatedFast = cached.doubleValue() - amount;
                    cachePut.accept(cacheKey, updatedFast);
                    return EconomyMutationResult.success(updatedFast);
                }
                select.setString(1, id);
                try (ResultSet rs = select.executeQuery()) {
                    double current = rs.next() ? rs.getDouble(1) : 0.0;
                    if (rows <= 0) return EconomyMutationResult.failure(current, "Insufficient funds");
                    cachePut.accept(cacheKey, current);
                    return EconomyMutationResult.success(current);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL withdraw failed: " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }

        String id = BalanceModel.idFor(uuid, currency);
        Object keyLock = lockManager.lockFor(id);
        synchronized (keyLock) {
            Double cached = cacheGet.apply(cacheKey);
            String withdrawSql = "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?";
            String selectSql = "SELECT balance FROM " + table + " WHERE id = ?";
            try {
                // If background persistence exists, flush pending deltas for consistency
                if (balanceBackgroundPersistence != null) {
                    try { balanceBackgroundPersistence.flushIdSync(id); } catch (Throwable ignored) {}
                }
                if (fallback == null || fallback.isClosed()) {
                    plugin.getLogger().severe("[EzEconomy] MySQL connection unavailable for fallback withdraw");
                    return EconomyMutationResult.failure(0.0, "Storage failure");
                }
                try (PreparedStatement withdraw = fallback.prepareStatement(withdrawSql);
                     PreparedStatement select = fallback.prepareStatement(selectSql)) {
                    withdraw.setDouble(1, amount);
                    withdraw.setString(2, id);
                    withdraw.setDouble(3, amount);
                    int rows = withdraw.executeUpdate();
                    if (rows > 0 && cached != null && canUseLocalFastBalanceResponse.get()) {
                        double updatedFast = cached.doubleValue() - amount;
                        cachePut.accept(cacheKey, updatedFast);
                        return EconomyMutationResult.success(updatedFast);
                    }
                    select.setString(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        double current = rs.next() ? rs.getDouble(1) : 0.0;
                        if (rows <= 0) return EconomyMutationResult.failure(current, "Insufficient funds");
                        cachePut.accept(cacheKey, current);
                        return EconomyMutationResult.success(current);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL withdraw failed (fallback): " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }
}
