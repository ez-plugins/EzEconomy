package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.test.DbTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.skyblockexp.ezeconomy.lock.LocalLockManager;

public class BalanceBackgroundFlushIntegrationTest {

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
        }

        plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        plugin.getConfig().set("performance.balance-cache.enabled", true);
        plugin.getConfig().set("caching-strategy", "LOCAL");
        plugin.setLockManager(new LocalLockManager());

        org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("mysql.table", "balances");
        cfg.set("mysql.host", "localhost");
        cfg.set("mysql.port", 3306);
        cfg.set("mysql.database", "test");
        cfg.set("mysql.username", "sa");
        cfg.set("mysql.password", "");
        // Use a short flush interval so background worker persists quickly
        cfg.set("mysql.balance-background-flush-interval-ms", 200);

        provider = new MySQLStorageProvider(plugin, cfg);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        hc.setUsername("sa");
        hc.setPassword("");
        ds = new HikariDataSource(hc);

        Field connField = MySQLStorageProvider.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(provider, conn);
        Field dsField = MySQLStorageProvider.class.getDeclaredField("hotPathDataSource");
        dsField.setAccessible(true);
        dsField.set(provider, ds);

        provider.initRepositories();
    }

    @AfterEach
    void teardown() throws Exception {
        try { if (provider != null) provider.shutdown(); } catch (Exception ignored) {}
        try { if (ds != null) ds.close(); } catch (Exception ignored) {}
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
    }

    @Test
    void pendingDeltasAreFlushedByBackgroundWorker() throws Exception {
        UUID u = UUID.randomUUID();
        String currency = "dollar";
        String id = com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel.idFor(u, currency);

        double amount = 100.0;
        EconomyMutationResult dep = provider.depositAndGetBalance(u, currency, amount);
        assertTrue(dep.isSuccess());

        // Immediately check DB via a fresh connection: not yet flushed
        try (Connection verifyConn = DriverManager.getConnection("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            try (var ps = verifyConn.prepareStatement("SELECT balance FROM balances WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    double before = rs.next() ? rs.getDouble(1) : 0.0;
                    // before flush, row may not exist yet
                    assertTrue(before == 0.0 || before == amount);
                }
            }
        }

        // Wait longer than flush interval for background worker to persist
        Thread.sleep(800);

        try (Connection verifyConn = DriverManager.getConnection("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            try (var ps = verifyConn.prepareStatement("SELECT balance FROM balances WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    double after = rs.next() ? rs.getDouble(1) : 0.0;
                    assertEquals(amount, after, 0.0001);
                }
            }
        }
    }
}
