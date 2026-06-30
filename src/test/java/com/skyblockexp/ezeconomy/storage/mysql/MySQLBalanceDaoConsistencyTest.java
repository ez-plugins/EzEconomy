package com.skyblockexp.ezeconomy.storage.mysql;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySQLBalanceDaoConsistencyTest {

    @Test
    void deposit_flushFailure_doesNotTouchDbOrCache() throws Exception {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        HikariDataSource pool = mock(HikariDataSource.class);
        MySQLBalanceBackgroundPersistenceService bg = mock(MySQLBalanceBackgroundPersistenceService.class);
        when(bg.flushIdSyncStrict(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        AtomicInteger cachePuts = new AtomicInteger(0);
        BiConsumer<String, Double> cachePut = (k, v) -> cachePuts.incrementAndGet();
        Function<String, Double> cacheGet = k -> 100.0;
        Supplier<Boolean> localFast = () -> true;

        MySQLBalanceDao dao = new MySQLBalanceDao(
                plugin,
                "balances",
                pool,
                null,
                new StripedLockManager(32),
                cacheGet,
                cachePut,
                localFast,
                bg
        );

        EconomyMutationResult result = dao.depositAndGetBalance(UUID.randomUUID(), "dollar", 25.0);

        assertFalse(result.isSuccess());
        assertEquals(0, cachePuts.get(), "Cache must not update when persistence precondition fails");
        verify(pool, never()).getConnection();
    }

    @Test
    void deposit_success_updatesCacheFromDbReadback() throws Exception {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        HikariDataSource pool = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement upsert = mock(PreparedStatement.class);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(pool.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("INSERT INTO balances"))).thenReturn(upsert);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("SELECT balance FROM balances"))).thenReturn(select);
        when(upsert.executeUpdate()).thenReturn(1);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getDouble(1)).thenReturn(175.0);

        AtomicReference<Double> cachedValue = new AtomicReference<Double>(null);
        BiConsumer<String, Double> cachePut = (k, v) -> cachedValue.set(v);

        MySQLBalanceDao dao = new MySQLBalanceDao(
                plugin,
                "balances",
                pool,
                null,
                new StripedLockManager(32),
                k -> 100.0,
                cachePut,
                () -> true,
                null
        );

        EconomyMutationResult result = dao.depositAndGetBalance(UUID.randomUUID(), "dollar", 25.0);

        assertTrue(result.isSuccess());
        assertEquals(175.0, result.getBalance());
        assertEquals(175.0, cachedValue.get(), 0.0001);
    }

    @Test
    void withdraw_sqlFailure_doesNotAdvanceCache() throws Exception {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        HikariDataSource pool = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement withdraw = mock(PreparedStatement.class);
        PreparedStatement select = mock(PreparedStatement.class);

        when(pool.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("UPDATE balances SET balance = balance - ?"))).thenReturn(withdraw);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("SELECT balance FROM balances"))).thenReturn(select);
        when(withdraw.executeUpdate()).thenThrow(new SQLException("Communications link failure", "08S01"));

        AtomicInteger cachePuts = new AtomicInteger(0);
        BiConsumer<String, Double> cachePut = (k, v) -> cachePuts.incrementAndGet();

        MySQLBalanceDao dao = new MySQLBalanceDao(
                plugin,
                "balances",
                pool,
                null,
                new StripedLockManager(32),
                k -> 150.0,
                cachePut,
                () -> true,
                null
        );

        EconomyMutationResult result = dao.withdrawAndGetBalance(UUID.randomUUID(), "dollar", 20.0);

        assertFalse(result.isSuccess());
        assertEquals(0, cachePuts.get(), "Cache must not update on failed withdraw persistence");
    }

    @Test
    void withdraw_insufficientFunds_doesNotAdvanceCache_andReturnsCurrentBalance() throws Exception {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        HikariDataSource pool = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement withdraw = mock(PreparedStatement.class);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(pool.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("UPDATE balances SET balance = balance - ?"))).thenReturn(withdraw);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.contains("SELECT balance FROM balances"))).thenReturn(select);
        when(withdraw.executeUpdate()).thenReturn(0);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getDouble(1)).thenReturn(130.0);

        AtomicInteger cachePuts = new AtomicInteger(0);
        BiConsumer<String, Double> cachePut = (k, v) -> cachePuts.incrementAndGet();

        MySQLBalanceDao dao = new MySQLBalanceDao(
                plugin,
                "balances",
                pool,
                null,
                new StripedLockManager(32),
                k -> 150.0,
                cachePut,
                () -> true,
                null
        );

        EconomyMutationResult result = dao.withdrawAndGetBalance(UUID.randomUUID(), "dollar", 200.0);

        assertFalse(result.isSuccess());
        assertEquals(130.0, result.getBalance(), 0.0001);
        assertEquals("Insufficient funds", result.getFailureReason());
        assertEquals(0, cachePuts.get(), "Cache must not update on insufficient-funds failure");
    }

    @Test
    void deposit_invalidFallbackConnection_refreshesAndSucceeds() throws Exception {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        Connection staleFallback = mock(Connection.class);
        Connection refreshedFallback = mock(Connection.class);
        PreparedStatement upsert = mock(PreparedStatement.class);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(staleFallback.isClosed()).thenReturn(false);
        when(staleFallback.isValid(2)).thenReturn(false);
        when(refreshedFallback.prepareStatement(org.mockito.ArgumentMatchers.contains("INSERT INTO balances"))).thenReturn(upsert);
        when(refreshedFallback.prepareStatement(org.mockito.ArgumentMatchers.contains("SELECT balance FROM balances"))).thenReturn(select);
        when(upsert.executeUpdate()).thenReturn(1);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getDouble(1)).thenReturn(125.0);

        AtomicInteger refreshCalls = new AtomicInteger(0);
        MySQLBalanceDao dao = new MySQLBalanceDao(
                plugin,
                "balances",
                null,
                staleFallback,
                new StripedLockManager(32),
                k -> 100.0,
                (k, v) -> { },
                () -> true,
                null,
                () -> {
                    refreshCalls.incrementAndGet();
                    return refreshedFallback;
                }
        );

        EconomyMutationResult result = dao.depositAndGetBalance(UUID.randomUUID(), "dollar", 25.0);

        assertTrue(result.isSuccess());
        assertEquals(125.0, result.getBalance(), 0.0001);
        assertEquals(1, refreshCalls.get(), "Fallback connection should be refreshed once after the stale connection is detected");
    }

    @Test
    void deposit_primaryConnectionFailure_refreshesPoolAndSucceeds() throws Exception {
        EzEconomyPlugin plugin = mock(EzEconomyPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        HikariDataSource stalePool = mock(HikariDataSource.class);
        HikariDataSource refreshedPool = mock(HikariDataSource.class);
        Connection staleConn = mock(Connection.class);
        Connection freshConn = mock(Connection.class);
        PreparedStatement upsert = mock(PreparedStatement.class);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(stalePool.getConnection()).thenThrow(new SQLException("Communications link failure", "08S01"));
        when(refreshedPool.getConnection()).thenReturn(freshConn);
        when(freshConn.prepareStatement(org.mockito.ArgumentMatchers.contains("INSERT INTO balances"))).thenReturn(upsert);
        when(freshConn.prepareStatement(org.mockito.ArgumentMatchers.contains("SELECT balance FROM balances"))).thenReturn(select);
        when(upsert.executeUpdate()).thenReturn(1);
        when(select.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getDouble(1)).thenReturn(175.0);

        AtomicReference<HikariDataSource> poolRef = new AtomicReference<HikariDataSource>(stalePool);
        AtomicInteger refreshCalls = new AtomicInteger(0);

        MySQLBalanceDao dao = new MySQLBalanceDao(
                plugin,
                "balances",
                poolRef::get,
                null,
                new StripedLockManager(32),
                k -> 150.0,
                (k, v) -> { },
                () -> true,
                null,
                () -> {
                    refreshCalls.incrementAndGet();
                    poolRef.set(refreshedPool);
                    return refreshedPool;
                },
                null
        );

        EconomyMutationResult result = dao.depositAndGetBalance(UUID.randomUUID(), "dollar", 25.0);

        assertTrue(result.isSuccess());
        assertEquals(175.0, result.getBalance(), 0.0001);
        assertEquals(1, refreshCalls.get(), "Primary pool should be refreshed once after connection failure");
        assertEquals(refreshedPool, poolRef.get(), "DAO should use the refreshed pool after reconnect");
    }
}
