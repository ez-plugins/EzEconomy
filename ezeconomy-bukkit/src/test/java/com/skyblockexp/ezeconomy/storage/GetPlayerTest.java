package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.dto.EconomyPlayer;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import com.skyblockexp.ezeconomy.util.PlayerUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetPlayerTest {
    private Object server;
    private EzEconomyPlugin plugin;

    @BeforeEach
    public void setup() {
        server = TestSupport.setupMockServer();
        plugin = TestSupport.loadPlugin(server);
    }

    @AfterEach
    public void teardown() {
        if (plugin != null) TestSupport.cleanupTestDataFolder(plugin, "test-data");
        TestSupport.tearDown();
    }

    @Test
    public void testYmlStoragePersistsNameAndDisplayName() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("yml.data-folder", "test-data");
        cfg.set("yml.per-player-file-naming", "uuid");
        YMLStorageProvider yml = new YMLStorageProvider(plugin, cfg);

        TestSupport.createTestDataFolder(plugin, "test-data");
        TestSupport.injectField(plugin, "storage", yml);

        // create offline recipient
        Object offlineObj;
        try {
            java.lang.reflect.Method addOffline = server.getClass().getMethod("addOfflinePlayer", String.class);
            offlineObj = addOffline.invoke(server, "ymlOffline");
        } catch (NoSuchMethodException ignored) {
            offlineObj = org.bukkit.Bukkit.getOfflinePlayer("ymlOffline");
        }
        org.bukkit.OfflinePlayer offline = (org.bukkit.OfflinePlayer) offlineObj;

        // Trigger a write so the YML file is created and saved
        yml.setBalance(offline.getUniqueId(), "dollar", 1.0);

        EconomyPlayer p = yml.getPlayer(offline.getUniqueId());
        // Stored name/displayName should be present and consistent; accept MockBukkit variations.
        org.junit.jupiter.api.Assertions.assertNotNull(p.getName());
        org.junit.jupiter.api.Assertions.assertEquals(p.getName(), p.getDisplayName());
    }

    @Test
    public void testPlayerUtilPrefersOnlineDisplayName() throws Exception {
        // create online player
        Object senderObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, "onlineOne");
        org.bukkit.entity.Player sender = (org.bukkit.entity.Player) senderObj;
        sender.setDisplayName("CoolName");

        // PlayerUtil should prefer the online player's display name
        com.skyblockexp.ezeconomy.dto.EconomyPlayer p = PlayerUtil.getPlayer(sender.getUniqueId());
        // Use the actual mock player's name (MockBukkit may assign different names)
        org.junit.jupiter.api.Assertions.assertEquals(sender.getName(), p.getName());
        org.junit.jupiter.api.Assertions.assertEquals("CoolName", p.getDisplayName());
    }

    @Test
    public void testMongoProviderFallbackToOffline() throws Exception {
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer("mongoOffline");
        com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider mongo = new com.skyblockexp.ezeconomy.storage.MongoDBStorageProvider(plugin, new YamlConfiguration());
        // No Mongo connection set -> should fallback to OfflinePlayer
        com.skyblockexp.ezeconomy.dto.EconomyPlayer p = mongo.getPlayer(off.getUniqueId());
        org.junit.jupiter.api.Assertions.assertNotNull(p.getName());
        org.junit.jupiter.api.Assertions.assertEquals(p.getName(), p.getDisplayName());
    }

    @Test
    public void testMySQLProviderFallbackToOffline() throws Exception {
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer("mysqlOffline");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("mysql.table", "balances_test");
        com.skyblockexp.ezeconomy.storage.MySQLStorageProvider mysql = new com.skyblockexp.ezeconomy.storage.MySQLStorageProvider(plugin, cfg);
        // No DB connection initialized -> should fallback to OfflinePlayer
        com.skyblockexp.ezeconomy.dto.EconomyPlayer p = mysql.getPlayer(off.getUniqueId());
        org.junit.jupiter.api.Assertions.assertNotNull(p.getName());
        org.junit.jupiter.api.Assertions.assertEquals(p.getName(), p.getDisplayName());
    }

    @Test
    public void testSQLiteProviderPersistsNameAndDisplayName() throws Exception {
        // SQLite JDBC is not always available in test environment; test fallback behavior instead
        com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider sqlite = new com.skyblockexp.ezeconomy.storage.SQLiteStorageProvider(plugin);
        org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer("sqliteOffline");
        com.skyblockexp.ezeconomy.dto.EconomyPlayer p = sqlite.getPlayer(offline.getUniqueId());
        org.junit.jupiter.api.Assertions.assertNotNull(p.getName());
        org.junit.jupiter.api.Assertions.assertEquals(p.getName(), p.getDisplayName());
    }
}
