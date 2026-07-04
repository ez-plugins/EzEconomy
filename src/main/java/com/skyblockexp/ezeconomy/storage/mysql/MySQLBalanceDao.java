package com.skyblockexp.ezeconomy.storage.mysql;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
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
    private final Supplier<HikariDataSource> poolSupplier;
    private volatile Connection fallback;
    private final StripedLockManager lockManager;
    private final Function<String, Double> cacheGet;
    private final BiConsumer<String, Double> cachePut;
    private final Supplier<Boolean> canUseLocalFastBalanceResponse;
    private final MySQLBalanceBackgroundPersistenceService balanceBackgroundPersistence;
    private final PoolRefresher primaryPoolRefresher;
    private final ConnectionRefresher fallbackConnectionRefresher;

    @FunctionalInterface
    public interface PoolRefresher {
        HikariDataSource refresh() throws Exception;
    }

    @FunctionalInterface
    public interface ConnectionRefresher {
        Connection refresh() throws Exception;
    }

    public MySQLBalanceDao(EzEconomyPlugin plugin,
                          String table,
                          HikariDataSource pool,
                          Connection fallback,
                          StripedLockManager lockManager,
                          Function<String, Double> cacheGet,
                          BiConsumer<String, Double> cachePut,
                          Supplier<Boolean> canUseLocalFastBalanceResponse,
                          MySQLBalanceBackgroundPersistenceService balanceBackgroundPersistence) {
        this(plugin, table, () -> pool, fallback, lockManager, cacheGet, cachePut, canUseLocalFastBalanceResponse, balanceBackgroundPersistence, null, null);
    }

    public MySQLBalanceDao(EzEconomyPlugin plugin,
                          String table,
                          Supplier<HikariDataSource> poolSupplier,
                          Connection fallback,
                          StripedLockManager lockManager,
                          Function<String, Double> cacheGet,
                          BiConsumer<String, Double> cachePut,
                          Supplier<Boolean> canUseLocalFastBalanceResponse,
                          MySQLBalanceBackgroundPersistenceService balanceBackgroundPersistence,
                          PoolRefresher primaryPoolRefresher,
                          ConnectionRefresher fallbackConnectionRefresher) {
        this.plugin = plugin;
        this.table = table;
        this.poolSupplier = poolSupplier;
        this.fallback = fallback;
        this.lockManager = lockManager;
        this.cacheGet = cacheGet;
        this.cachePut = cachePut;
        this.canUseLocalFastBalanceResponse = canUseLocalFastBalanceResponse;
        this.balanceBackgroundPersistence = balanceBackgroundPersistence;
        this.primaryPoolRefresher = primaryPoolRefresher;
        this.fallbackConnectionRefresher = fallbackConnectionRefresher;
    }

    private String balanceCacheKey(UUID uuid, String currency) {
        return "bal:" + uuid + ":" + currency;
    }

    public EconomyMutationResult depositAndGetBalance(UUID uuid, String currency, double amount) {
        String cacheKey = balanceCacheKey(uuid, currency);
        HikariDataSource activePool = resolvePool();
        if (activePool != null) {
            return depositPrimary(uuid, currency, amount, cacheKey, activePool, true);
        }

        return depositFallback(uuid, currency, amount, true);
    }

    private EconomyMutationResult depositPrimary(UUID uuid, String currency, double amount, String cacheKey, HikariDataSource activePool, boolean allowReconnectRetry) {
        String id = BalanceModel.idFor(uuid, currency);
        if (balanceBackgroundPersistence != null && !balanceBackgroundPersistence.flushIdSyncStrict(id)) {
            plugin.getLogger().severe("[EzEconomy] MySQL deposit aborted because pending balance flush failed for " + id);
            return EconomyMutationResult.failure(0.0, "Storage failure");
        }

        try (Connection conn = activePool.getConnection();
             PreparedStatement upsert = conn.prepareStatement(
                     "INSERT INTO " + table + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)"
             );
             PreparedStatement select = conn.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
            upsert.setString(1, id);
            upsert.setString(2, uuid.toString());
            upsert.setString(3, currency);
            upsert.setDouble(4, amount);
            upsert.executeUpdate();
            select.setString(1, id);
            try (ResultSet rs = select.executeQuery()) {
                double updated = rs.next() ? rs.getDouble(1) : amount;
                cachePut.accept(cacheKey, updated);
                return EconomyMutationResult.success(updated);
            }
        } catch (SQLException e) {
            if (allowReconnectRetry && isConnectionFailure(e) && tryRecoverPrimaryConnection()) {
                return depositPrimary(uuid, currency, amount, cacheKey, resolvePool(), false);
            }
            plugin.getLogger().severe("[EzEconomy] MySQL deposit failed: " + e.getMessage());
            return EconomyMutationResult.failure(0.0, "Storage failure");
        }
    }

    private EconomyMutationResult depositFallback(UUID uuid, String currency, double amount, boolean allowReconnectRetry) {
        String cacheKey = balanceCacheKey(uuid, currency);
        String id = BalanceModel.idFor(uuid, currency);
        Object keyLock = lockManager.lockFor(id);
        synchronized (keyLock) {
            String upsertSql = "INSERT INTO " + table + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)";
            String selectSql = "SELECT balance FROM " + table + " WHERE id = ?";
            try {
                if (balanceBackgroundPersistence != null && !balanceBackgroundPersistence.flushIdSyncStrict(id)) {
                    plugin.getLogger().severe("[EzEconomy] MySQL deposit aborted because pending balance flush failed for " + id);
                    return EconomyMutationResult.failure(0.0, "Storage failure");
                }
                Connection conn = ensureFallbackConnection();
                try (PreparedStatement upsert = conn.prepareStatement(upsertSql);
                     PreparedStatement select = conn.prepareStatement(selectSql)) {
                    upsert.setString(1, id);
                    upsert.setString(2, uuid.toString());
                    upsert.setString(3, currency);
                    upsert.setDouble(4, amount);
                    upsert.executeUpdate();
                    select.setString(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        double updated = rs.next() ? rs.getDouble(1) : amount;
                        cachePut.accept(cacheKey, updated);
                        return EconomyMutationResult.success(updated);
                    }
                }
            } catch (SQLException e) {
                if (allowReconnectRetry && tryRecoverFallbackConnection(e)) {
                    return depositFallback(uuid, currency, amount, false);
                }
                plugin.getLogger().severe("[EzEconomy] MySQL deposit failed (fallback): " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    public EconomyMutationResult withdrawAndGetBalance(UUID uuid, String currency, double amount) {
        String cacheKey = balanceCacheKey(uuid, currency);
        HikariDataSource activePool = resolvePool();
        if (activePool != null) {
            return withdrawPrimary(uuid, currency, amount, cacheKey, activePool, true);
        }

        return withdrawFallback(uuid, currency, amount, true);
    }

    private EconomyMutationResult withdrawPrimary(UUID uuid, String currency, double amount, String cacheKey, HikariDataSource activePool, boolean allowReconnectRetry) {
        String id = BalanceModel.idFor(uuid, currency);
        if (balanceBackgroundPersistence != null && !balanceBackgroundPersistence.flushIdSyncStrict(id)) {
            plugin.getLogger().severe("[EzEconomy] MySQL withdraw aborted because pending balance flush failed for " + id);
            return EconomyMutationResult.failure(0.0, "Storage failure");
        }

        try (Connection conn = activePool.getConnection();
             PreparedStatement withdraw = conn.prepareStatement(
                     "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?"
             );
             PreparedStatement select = conn.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
            withdraw.setDouble(1, amount);
            withdraw.setString(2, id);
            withdraw.setDouble(3, amount);
            int rows = withdraw.executeUpdate();
            select.setString(1, id);
            try (ResultSet rs = select.executeQuery()) {
                double current = rs.next() ? rs.getDouble(1) : 0.0;
                if (rows <= 0) return EconomyMutationResult.failure(current, "Insufficient funds");
                cachePut.accept(cacheKey, current);
                return EconomyMutationResult.success(current);
            }
        } catch (SQLException e) {
            if (allowReconnectRetry && isConnectionFailure(e) && tryRecoverPrimaryConnection()) {
                return withdrawPrimary(uuid, currency, amount, cacheKey, resolvePool(), false);
            }
            plugin.getLogger().severe("[EzEconomy] MySQL withdraw failed: " + e.getMessage());
            return EconomyMutationResult.failure(0.0, "Storage failure");
        }
    }

    private EconomyMutationResult withdrawFallback(UUID uuid, String currency, double amount, boolean allowReconnectRetry) {
        String cacheKey = balanceCacheKey(uuid, currency);
        String id = BalanceModel.idFor(uuid, currency);
        Object keyLock = lockManager.lockFor(id);
        synchronized (keyLock) {
            String withdrawSql = "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?";
            String selectSql = "SELECT balance FROM " + table + " WHERE id = ?";
            try {
                if (balanceBackgroundPersistence != null && !balanceBackgroundPersistence.flushIdSyncStrict(id)) {
                    plugin.getLogger().severe("[EzEconomy] MySQL withdraw aborted because pending balance flush failed for " + id);
                    return EconomyMutationResult.failure(0.0, "Storage failure");
                }
                Connection conn = ensureFallbackConnection();
                try (PreparedStatement withdraw = conn.prepareStatement(withdrawSql);
                     PreparedStatement select = conn.prepareStatement(selectSql)) {
                    withdraw.setDouble(1, amount);
                    withdraw.setString(2, id);
                    withdraw.setDouble(3, amount);
                    int rows = withdraw.executeUpdate();
                    select.setString(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        double current = rs.next() ? rs.getDouble(1) : 0.0;
                        if (rows <= 0) return EconomyMutationResult.failure(current, "Insufficient funds");
                        cachePut.accept(cacheKey, current);
                        return EconomyMutationResult.success(current);
                    }
                }
            } catch (SQLException e) {
                if (allowReconnectRetry && tryRecoverFallbackConnection(e)) {
                    return withdrawFallback(uuid, currency, amount, false);
                }
                plugin.getLogger().severe("[EzEconomy] MySQL withdraw failed (fallback): " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    private Connection ensureFallbackConnection() throws SQLException {
        if (fallback != null) {
            try {
                if (!fallback.isClosed() && fallback.isValid(2)) {
                    return fallback;
                }
            } catch (SQLException ignored) {
                // treat as disconnected and attempt refresh below
            }
        }
        if (fallbackConnectionRefresher != null) {
            try {
                Connection refreshed = fallbackConnectionRefresher.refresh();
                if (refreshed != null) {
                    this.fallback = refreshed;
                    return refreshed;
                }
            } catch (Exception e) {
                throw new SQLException("MySQL connection unavailable for fallback operation", e);
            }
        }
        throw new SQLException("MySQL connection unavailable for fallback operation");
    }

    private boolean tryRecoverFallbackConnection(SQLException e) {
        if (fallbackConnectionRefresher == null || !isConnectionFailure(e)) {
            return false;
        }
        try {
            fallbackConnectionRefresher.refresh();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private HikariDataSource resolvePool() {
        return poolSupplier == null ? null : poolSupplier.get();
    }

    private boolean tryRecoverPrimaryConnection() {
        if (primaryPoolRefresher == null) {
            return false;
        }
        try {
            return primaryPoolRefresher.refresh() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isConnectionFailure(SQLException ex) {
        if (ex instanceof SQLTransientConnectionException || ex instanceof SQLNonTransientConnectionException) {
            return true;
        }
        String sqlState = ex.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && (msg.contains("Communications link failure") || msg.contains("Connection reset") || msg.contains("Connection is closed"));
    }
}
