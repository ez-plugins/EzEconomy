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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import com.skyblockexp.ezeconomy.storage.MySQLStorageProvider;
import com.skyblockexp.ezeconomy.lock.LocalLockManager;
import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import com.skyblockexp.ezeconomy.storage.TransferResult;

public class MultiPaymentIntegrationTest {

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
        // keep background flush interval high to avoid flakiness in assertions
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
    void sequentialDepositsWithdrawsAndPaysValidateBalances() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();
        String currency = "dollar";

        // Use setBalance to initialize DB-backed balances deterministically
        provider.setBalance(alice, currency, 1000.0);
        provider.setBalance(bob, currency, 500.0);
        provider.setBalance(charlie, currency, 250.0);

        assertEquals(1000.0, provider.getBalance(alice, currency), 0.0001);
        assertEquals(500.0, provider.getBalance(bob, currency), 0.0001);
        assertEquals(250.0, provider.getBalance(charlie, currency), 0.0001);

        // Alice pays Bob 200
        TransferResult t1 = provider.transfer(alice, bob, currency, 200.0, 200.0);
        assertTrue(t1.isSuccess());
        assertEquals(800.0, t1.getFromBalance(), 0.0001);
        assertEquals(700.0, t1.getToBalance(), 0.0001);

        // Bob withdraws 100 (simulate by transferring to a sink account without credit)
        UUID sink = UUID.randomUUID();
        provider.setBalance(sink, currency, 0.0);
        TransferResult w1 = provider.transfer(bob, sink, currency, 100.0, 0.0);
        assertTrue(w1.isSuccess());
        assertEquals(600.0, w1.getFromBalance(), 0.0001);

        // Bob pays Charlie 300
        TransferResult t2 = provider.transfer(bob, charlie, currency, 300.0, 300.0);
        assertTrue(t2.isSuccess());
        assertEquals(300.0, t2.getFromBalance(), 0.0001);
        assertEquals(550.0, t2.getToBalance(), 0.0001);

        // Charlie attempts to withdraw more than balance
        EconomyMutationResult w2 = provider.withdrawAndGetBalance(charlie, currency, 1000.0);
        assertFalse(w2.isSuccess());
        // balances unchanged for failed withdraw
        assertEquals(550.0, provider.getBalance(charlie, currency), 0.0001);

        // final verification of all balances
        assertEquals(800.0, provider.getBalance(alice, currency), 0.0001);
        assertEquals(300.0, provider.getBalance(bob, currency), 0.0001);
        assertEquals(550.0, provider.getBalance(charlie, currency), 0.0001);
    }

    @Test
    void concurrentTransfers_preserveTotalAndNoNegativeBalances() throws Exception {
        int players = 6;
        List<UUID> uuids = new ArrayList<>();
        String currency = "dollar";
        double initial = 1000.0;
        double totalInitial = players * initial;
        for (int i = 0; i < players; i++) {
            UUID u = UUID.randomUUID();
            uuids.add(u);
            EconomyMutationResult r = provider.depositAndGetBalance(u, currency, initial);
            assertTrue(r.isSuccess());
        }

        ExecutorService ex = Executors.newFixedThreadPool(8);
        Random rnd = new Random(12345);
        int tasks = 500;
        for (int i = 0; i < tasks; i++) {
            ex.submit(() -> {
                int a = rnd.nextInt(players);
                int b = rnd.nextInt(players - 1);
                if (b >= a) b = b + 1;
                UUID from = uuids.get(a);
                UUID to = uuids.get(b);
                double amt = 1 + rnd.nextInt(50);
                try {
                    provider.transfer(from, to, currency, amt, amt);
                } catch (Throwable ignored) {}
            });
        }
        ex.shutdown();
        assertTrue(ex.awaitTermination(60, TimeUnit.SECONDS), "Executor did not terminate");

        double total = 0.0;
        for (UUID u : uuids) {
            double b = provider.getBalance(u, currency);
            assertTrue(b >= 0.0, "Balance should not be negative");
            total += b;
        }

        assertEquals(totalInitial, total, 0.0001, "Total funds should be conserved after transfers");
    }
}
