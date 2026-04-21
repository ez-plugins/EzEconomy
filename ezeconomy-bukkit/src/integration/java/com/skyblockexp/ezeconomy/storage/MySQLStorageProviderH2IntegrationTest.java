package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.test.DbTestHelper;
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

public class MySQLStorageProviderH2IntegrationTest {

    private Connection conn;

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
    }

    @AfterEach
    void teardown() throws Exception {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
    }

    @Test
    void setGetDepositWithdrawFlow() throws Exception {
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("mysql.table", "balances");
        cfg.set("mysql.host", "localhost");
        cfg.set("mysql.port", 3306);
        cfg.set("mysql.database", "test");
        cfg.set("mysql.username", "sa");
        cfg.set("mysql.password", "");

        MySQLStorageProvider provider = new MySQLStorageProvider(plugin, cfg);

        // Inject the H2 connection into the provider using reflection
        Field connField = MySQLStorageProvider.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(provider, conn);

        // Initialise Jaloquent repositories with the injected connection
        provider.initRepositories();

        UUID u = UUID.randomUUID();
        // Initially zero
        assertEquals(0.0, provider.getBalance(u, "dollar"), 0.0001);

        provider.setBalance(u, "dollar", 123.45);
        assertEquals(123.45, provider.getBalance(u, "dollar"), 0.0001);

        // withdraw portion that is available
        assertTrue(provider.tryWithdraw(u, "dollar", 23.45));
        assertEquals(100.0, provider.getBalance(u, "dollar"), 0.0001);

        // withdraw more than available should fail
        assertFalse(provider.tryWithdraw(u, "dollar", 200.0));
        assertEquals(100.0, provider.getBalance(u, "dollar"), 0.0001);

        // deposit
        provider.deposit(u, "dollar", 50.0);
        assertEquals(150.0, provider.getBalance(u, "dollar"), 0.0001);

        // transfer between two accounts
        UUID v = UUID.randomUUID();
        com.skyblockexp.ezeconomy.storage.TransferResult tr = provider.transfer(u, v, "dollar", 25.0, 25.0);
        assertTrue(tr.isSuccess());
        assertEquals(125.0, provider.getBalance(u, "dollar"), 0.0001);
        assertEquals(25.0, provider.getBalance(v, "dollar"), 0.0001);

        try { plugin.getServer().getPluginManager().disablePlugin(plugin); } catch (Exception ignored) {}
    }
}
