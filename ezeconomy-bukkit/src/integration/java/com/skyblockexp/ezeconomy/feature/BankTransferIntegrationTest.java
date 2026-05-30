package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.test.DbTestHelper;
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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class BankTransferIntegrationTest {

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
    void bankTransferMovesFundsBetweenPlayers() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String currency = "dollar";

        provider.depositAndGetBalance(a, currency, 150.0);
        provider.depositAndGetBalance(b, currency, 50.0);

        com.skyblockexp.ezeconomy.storage.TransferResult tr = provider.transfer(a, b, currency, 100.0, 100.0);
        assertTrue(tr.isSuccess());
        assertEquals(50.0, tr.getFromBalance(), 0.0001);
        assertEquals(150.0, tr.getToBalance(), 0.0001);
    }
}
