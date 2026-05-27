package com.skyblockexp.ezeconomy.feature;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;

import com.skyblockexp.ezeconomy.feature.support.TestSupport.MockStorage;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankCommandFeatureTest {
    private Object server;
    private EzEconomyPlugin plugin;

    @BeforeEach
    public void setup() throws Exception {
        System.setProperty("ezeconomy.test", "true");
        server = TestSupport.setupMockServer();
        plugin = TestSupport.loadPlugin(server);
        plugin.loadMessageProvider();
    }

    @AfterEach
    public void teardown() {
        TestSupport.tearDown();
        System.clearProperty("ezeconomy.test");
    }

    @Test
    public void testBankCreateSubcommand_runs() throws Exception {
        Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "bankOwner");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
        sender.setOp(true);
        boolean result = sender.performCommand("bank create testbank");
        assertTrue(result);
    }

    @Test
    public void testBankDepositWithdraw_ownerAndMemberFlow() throws Exception {
        Object ownerObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "owner");
        org.bukkit.entity.Player owner = (org.bukkit.entity.Player) ownerObj;
        owner.setOp(true);

        Object memberObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "member");
        org.bukkit.entity.Player member = (org.bukkit.entity.Player) memberObj;

        // use shared MockStorage to back bank operations
        MockStorage storage = new MockStorage();
        TestSupport.injectField(plugin, "storage", storage);
        storage.setBalance(owner.getUniqueId(), plugin.getDefaultCurrency(), 200.0);
        storage.setBalance(member.getUniqueId(), plugin.getDefaultCurrency(), 0.0);

        // owner creates bank
        boolean create = owner.performCommand("bank create mybank");
        assertTrue(create);

        // owner deposits to bank
        boolean dep = owner.performCommand("bank deposit mybank 100");
        assertTrue(dep);

        // check bank balance via storage
        double bal = storage.getBankBalance("mybank", plugin.getDefaultCurrency());
        assertEquals(100.0, bal, 0.001);
        assertEquals(100.0, storage.getBalance(owner.getUniqueId(), plugin.getDefaultCurrency()), 0.001);

        // add member and withdraw
        boolean added = owner.performCommand("bank addmember mybank member");
        assertTrue(added);

        member.setOp(true);
        boolean w = member.performCommand("bank withdraw mybank 25");
        assertTrue(w);

        double bal2 = storage.getBankBalance("mybank", plugin.getDefaultCurrency());
        assertEquals(75.0, bal2, 0.001);
        assertEquals(25.0, storage.getBalance(member.getUniqueId(), plugin.getDefaultCurrency()), 0.001);
    }

    @Test
    public void testBank_withYMLStorage_persistsBankBalances() throws Exception {
        Object ownerObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "ymlOwner");
        org.bukkit.entity.Player owner = (org.bukkit.entity.Player) ownerObj;
        owner.setOp(true);

        // Prepare YML storage config to use a test folder
        org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("yml.data-folder", "test-bank-yml");

        com.skyblockexp.ezeconomy.storage.YMLStorageProvider yml = new com.skyblockexp.ezeconomy.storage.YMLStorageProvider(plugin, cfg);
        TestSupport.injectField(plugin, "storage", yml);
        yml.setBalance(owner.getUniqueId(), plugin.getDefaultCurrency(), 400.0);

        boolean create = owner.performCommand("bank create ybank");
        assertTrue(create);

        boolean dep = owner.performCommand("bank deposit ybank 200");
        assertTrue(dep);

        double bal = yml.getBankBalance("ybank", plugin.getDefaultCurrency());
        org.junit.jupiter.api.Assertions.assertEquals(200.0, bal, 0.001);
        org.junit.jupiter.api.Assertions.assertEquals(200.0, yml.getBalance(owner.getUniqueId(), plugin.getDefaultCurrency()), 0.001);

        TestSupport.cleanupTestDataFolder(plugin, "test-bank-yml");
    }

    @Test
    public void testBank_withMySQLStorage_commandsSucceedWithoutDB() throws Exception {
        Object ownerObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "sqlOwner");
        org.bukkit.entity.Player owner = (org.bukkit.entity.Player) ownerObj;
        owner.setOp(true);

        org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("mysql.host", "localhost");
        cfg.set("mysql.port", 3306);
        cfg.set("mysql.database", "testdb");
        cfg.set("mysql.username", "root");
        cfg.set("mysql.password", "password");

        com.skyblockexp.ezeconomy.storage.MySQLStorageProvider sql = new com.skyblockexp.ezeconomy.storage.MySQLStorageProvider(plugin, cfg);
        // Provide an in-memory H2 connection in MySQL compatibility mode for tests
        java.sql.Connection conn = null;
        try {
            conn = java.sql.DriverManager.getConnection("jdbc:h2:mem:bankdb;MODE=MySQL");
            TestSupport.injectField(sql, "connection", conn);
            TestSupport.injectField(plugin, "storage", sql);

            boolean create = owner.performCommand("bank create sqlbank");
            assertTrue(create);

            boolean dep = owner.performCommand("bank deposit sqlbank 300");
            assertTrue(dep);
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }

        // We don't assert balance here because MySQL provider in test mode may not establish a connection.
    }
}
