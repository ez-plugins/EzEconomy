package com.skyblockexp.ezeconomy.util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerLookupTest {
    private Object server;

    @BeforeEach
    public void setup() throws Exception {
        try {
            server = org.mockbukkit.mockbukkit.MockBukkit.mock();
        } catch (IllegalStateException e) {
            org.mockbukkit.mockbukkit.MockBukkit.unmock();
            server = org.mockbukkit.mockbukkit.MockBukkit.mock();
        }
        org.bukkit.plugin.java.JavaPlugin plugin = (org.bukkit.plugin.java.JavaPlugin) org.mockbukkit.mockbukkit.MockBukkit.load(com.skyblockexp.ezeconomy.core.EzEconomyPlugin.class);
        try { plugin.getClass().getMethod("registerEconomy").invoke(plugin); } catch (Exception ignored) {}
        // Ensure cache is in a known state
        com.skyblockexp.ezeconomy.util.PlayerLookup.refreshCache();
    }

    @AfterEach
    public void teardown() {
        try { org.mockbukkit.mockbukkit.MockBukkit.unmock(); } catch (Exception ignored) {}
    }

    @Test
    public void findByName_prefersOnlineExactMatch() throws Exception {
        Object p = server.getClass().getMethod("addPlayer", String.class).invoke(server, "Derek");
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) p;
        var opt = com.skyblockexp.ezeconomy.util.PlayerLookup.findByName("Derek");
        assertTrue(opt.isPresent());
        assertEquals(player.getUniqueId(), opt.get().getUniqueId());
    }

    @Test
    public void namesStartingWith_returnsMatchingNames() throws Exception {
        server.getClass().getMethod("addPlayer", String.class).invoke(server, "Alice");
        server.getClass().getMethod("addPlayer", String.class).invoke(server, "Alina");
        server.getClass().getMethod("addPlayer", String.class).invoke(server, "Bob");
        // rebuild cache from current offline players
        com.skyblockexp.ezeconomy.util.PlayerLookup.refreshCache();
        List<String> matches = com.skyblockexp.ezeconomy.util.PlayerLookup.namesStartingWith("Al");
        assertTrue(matches.contains("Alice"));
        assertTrue(matches.contains("Alina"));
        assertFalse(matches.contains("Bob"));
    }

    @Test
    public void addAndRemoveCache_updatesNames() throws Exception {
        String name = "Eve_" + System.nanoTime();
        Object p = server.getClass().getMethod("addPlayer", String.class).invoke(server, name);
        PlayerMock player = (PlayerMock) p;
        // ensure added to cache then removed
        com.skyblockexp.ezeconomy.util.PlayerLookup.addToCache(player);
        String key = name.toLowerCase(Locale.ROOT);
        // inspect private CACHE via reflection to avoid flaky public helpers
        Field f = com.skyblockexp.ezeconomy.util.PlayerLookup.class.getDeclaredField("CACHE");
        f.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, ?> cache = (Map<String, ?>) f.get(null);
        assertTrue(cache.containsKey(key));
        com.skyblockexp.ezeconomy.util.PlayerLookup.removeFromCache(player);
        assertFalse(cache.containsKey(key));
    }

    @Test
    public void refreshCache_rebuildsFromServer() throws Exception {
        server.getClass().getMethod("addPlayer", String.class).invoke(server, "Frank");
        com.skyblockexp.ezeconomy.util.PlayerLookup.refreshCache();
        var opt = com.skyblockexp.ezeconomy.util.PlayerLookup.findByName("Frank");
        assertTrue(opt.isPresent());
    }

    @Test
    public void findByName_returnsEmptyForUnknown() {
        var opt = com.skyblockexp.ezeconomy.util.PlayerLookup.findByName("NoSuchPlayer123");
        assertTrue(opt.isEmpty());
    }
}
