package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.test.DbTestHelper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.lock.LocalLockManager;
import com.skyblockexp.ezeconomy.cache.CacheManager;
import com.skyblockexp.ezeconomy.cache.ExpiringCache;
import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;

public class FeatureDepositWithdrawLockCacheTest {

    private Connection conn;
    private HikariDataSource ds;
    private EzEconomyPlugin plugin;
    private MySQLStorageProvider provider;

    @BeforeEach
    void setup() throws Exception {
        try { MockBukkit.mock(); } catch (IllegalStateException e) { MockBukkit.unmock(); MockBukkit.mock(); }
        conn = DbTestHelper.createH2MemoryMysql();
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS balances (id VARCHAR(69) NOT NULL, uuid VARCHAR(36), currency VARCHAR(32), balance DOUBLE, PRIMARY KEY (id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS players (id VARCHAR(36) PRIMARY KEY, name VARCHAR(64), displayName VARCHAR(128))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS banks (id VARCHAR(97) PRIMARY KEY, name VARCHAR(64), currency VARCHAR(32), balance DOUBLE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bank_members (id VARCHAR(101) PRIMARY KEY, bank VARCHAR(64), uuid VARCHAR(36), owner BOOLEAN)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS transactions (id VARCHAR(36) PRIMARY KEY, uuid VARCHAR(36), currency VARCHAR(32), amount DOUBLE, timestamp BIGINT)");
        }

        plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        // Ensure cache strategy and balance cache enabled for fast-path behaviour
        plugin.getConfig().set("performance.balance-cache.enabled", true);
        plugin.getConfig().set("caching-strategy", "LOCAL");
        plugin.setLockManager(new LocalLockManager());

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("mysql.table", "balances");
        cfg.set("mysql.host", "localhost");
        cfg.set("mysql.port", 3306);
        cfg.set("mysql.database", "test");
        cfg.set("mysql.username", "sa");
        cfg.set("mysql.password", "");
        // Increase background flush interval in tests to avoid race with worker
        cfg.set("mysql.balance-background-flush-interval-ms", 10000);

        provider = new MySQLStorageProvider(plugin, cfg);

        // Create a Hikari pool pointing at the same H2 memory DB so background persistence is enabled
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        hc.setUsername("sa");
        hc.setPassword("");
        hc.setMaximumPoolSize(2);
        ds = new HikariDataSource(hc);

        // Inject the H2 connection and Hikari pool into the provider via reflection
        Field connField = MySQLStorageProvider.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(provider, conn);
        Field dsField = MySQLStorageProvider.class.getDeclaredField("hotPathDataSource");
        dsField.setAccessible(true);
        dsField.set(provider, ds);

        // Initialise repositories which also starts background persistence
        provider.initRepositories();
    }

    @AfterEach
    void teardown() throws Exception {
        try { if (provider != null) provider.shutdown(); } catch (Exception ignored) {}
        try { if (ds != null) ds.close(); } catch (Exception ignored) {}
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
        // Clear cache entries to avoid cross-test pollution
        try { CacheManager.getProvider().remove("bal:dummy"); } catch (Throwable ignored) {}
    }

    @Test
    void depositWithdrawLockAndCacheFlow() throws Exception {
        UUID u = UUID.randomUUID();
        String currency = "dollar";
        String cacheKey = "bal:" + u + ":" + currency;

        EconomyMutationResult dep = provider.depositAndGetBalance(u, currency, 200.0);
        // provider and background persistence internal state inspected during earlier debugging; not printed here
        assertTrue(dep.isSuccess());
        assertEquals(200.0, dep.getBalance(), 0.0001);

        ExpiringCache.Entry<?> e = CacheManager.getProvider().getEntry(cacheKey);
        assertNotNull(e, "Cache should contain entry after deposit");
        assertTrue(e.value instanceof Number, "Cached value should be numeric");
        assertEquals(200.0, ((Number) e.value).doubleValue(), 0.0001);
        assertTrue(e.expiresAt > System.currentTimeMillis());

        // Lock manager should be present and allow acquire/release
        String token = plugin.getLockManager().acquire(u, plugin.getLockTtlMs(), plugin.getLockRetryMs(), plugin.getLockMaxAttempts());
        assertNotNull(token, "Should acquire local lock");
        assertTrue(plugin.getLockManager().release(u, token), "Should release local lock");

        EconomyMutationResult w = provider.withdrawAndGetBalance(u, currency, 50.0);
        assertTrue(w.isSuccess());
        assertEquals(150.0, w.getBalance(), 0.0001);

        ExpiringCache.Entry<?> e2 = CacheManager.getProvider().getEntry(cacheKey);
        assertNotNull(e2, "Cache entry should remain after withdraw");
        assertTrue(e2.value instanceof Number, "Cached value should be numeric");
        assertEquals(150.0, ((Number) e2.value).doubleValue(), 0.0001);
    }
}
