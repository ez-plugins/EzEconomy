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
}
