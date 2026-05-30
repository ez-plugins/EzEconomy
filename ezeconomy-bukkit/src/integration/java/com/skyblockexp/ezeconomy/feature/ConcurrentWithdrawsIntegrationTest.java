package com.skyblockexp.ezeconomy.feature;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.test.DbTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

import static org.junit.jupiter.api.Assertions.*;

import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.skyblockexp.ezeconomy.lock.LocalLockManager;

public class ConcurrentWithdrawsIntegrationTest {

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
        // Keep background flush interval long so reservations are visible during the concurrent phase
        cfg.set("mysql.balance-background-flush-interval-ms", 10000);

        provider = new MySQLStorageProvider(plugin, cfg);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        hc.setUsername("sa");
        hc.setPassword("");
        hc.setMaximumPoolSize(4);
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
    void concurrentWithdrawsReserveAndPersist() throws Exception {
        UUID u = UUID.randomUUID();
        String currency = "dollar";
        String id = com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel.idFor(u, currency);

        double initial = 200.0;
        EconomyMutationResult dep = provider.depositAndGetBalance(u, currency, initial);
        assertTrue(dep.isSuccess());

        int threads = 10;
        double withdrawAmt = 30.0;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        DoubleAdder successSum = new DoubleAdder();

        for (int i = 0; i < threads; i++) {
            ex.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    EconomyMutationResult r = provider.withdrawAndGetBalance(u, currency, withdrawAmt);
                    if (r.isSuccess()) {
                        successCount.incrementAndGet();
                        successSum.add(withdrawAmt);
                    }
                } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            });
        }

        // start all threads together
        ready.await();
        start.countDown();

        ex.shutdown();
        assertTrue(ex.awaitTermination(10, TimeUnit.SECONDS));

        double totalWithdrawn = successSum.sum();
        // invariants: can't withdraw more than initial
        assertTrue(totalWithdrawn <= initial + 1e-9, "Total withdrawn should not exceed initial balance");

        // flush pending to DB
        provider.shutdown();

        // Verify final balance using a fresh JDBC connection (provider.shutdown() may close the injected connection)
        String url = "jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (java.sql.Connection verifyConn = java.sql.DriverManager.getConnection(url, "sa", "");
             PreparedStatement ps = verifyConn.prepareStatement("SELECT balance FROM balances WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                double dbBal = rs.next() ? rs.getDouble(1) : 0.0;
                assertEquals(initial - totalWithdrawn, dbBal, 0.0001);
            }
        }
    }
}
