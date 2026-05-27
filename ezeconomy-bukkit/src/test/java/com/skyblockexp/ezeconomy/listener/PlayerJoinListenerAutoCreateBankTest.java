package com.skyblockexp.ezeconomy.listener;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.feature.support.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for banking.auto-create-on-join: when a player joins for the first time a personal
 * bank named after the player is created automatically if the feature is enabled.
 */
public class PlayerJoinListenerAutoCreateBankTest {

    private Object server;
    private EzEconomyPlugin plugin;

    @BeforeEach
    public void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = TestSupport.loadPlugin(server);
        // Default: banking enabled, auto-create enabled
        plugin.getConfig().set("banking.enabled", true);
        plugin.getConfig().set("banking.auto-create-on-join", true);
    }

    @AfterEach
    public void teardown() {
        MockBukkit.unmock();
    }

    // --- helpers ---

    private org.bukkit.entity.Player addPlayer(String name) throws Exception {
        Object playerObj = server.getClass().getMethod("addPlayer", String.class).invoke(server, name);
        return (org.bukkit.entity.Player) playerObj;
    }

    private TestSupport.MockStorage injectMockStorage() {
        TestSupport.MockStorage storage = new TestSupport.MockStorage();
        TestSupport.injectField(plugin, "storage", storage);
        return storage;
    }

    // --- tests ---

    @Test
    public void autoCreateOnJoin_createsBankNamedAfterPlayer() throws Exception {
        // Arrange
        TestSupport.MockStorage storage = injectMockStorage();

        // Act — joining triggers PlayerJoinEvent which the listener handles
        addPlayer("Alice");
        // The bank is created asynchronously; give it a moment
        Thread.sleep(200);

        // Assert — a bank with the player's name was created
        assertTrue(storage.bankExists("Alice"),
                "A bank named 'Alice' should have been auto-created on join");
    }

    @Test
    public void autoCreateOnJoin_disabled_doesNotCreateBank() throws Exception {
        // Arrange
        plugin.getConfig().set("banking.auto-create-on-join", false);
        TestSupport.MockStorage storage = injectMockStorage();

        // Act
        addPlayer("Bob");
        Thread.sleep(200);

        // Assert — no bank created
        assertFalse(storage.bankExists("Bob"),
                "No bank should be created when auto-create-on-join is disabled");
    }

    @Test
    public void autoCreateOnJoin_bankingDisabled_doesNotCreateBank() throws Exception {
        // Arrange
        plugin.getConfig().set("banking.enabled", false);
        TestSupport.MockStorage storage = injectMockStorage();

        // Act
        addPlayer("Carol");
        Thread.sleep(200);

        // Assert
        assertFalse(storage.bankExists("Carol"),
                "No bank should be created when the banking subsystem is disabled");
    }

    @Test
    public void autoCreateOnJoin_existingBank_isNotDuplicated() throws Exception {
        // Arrange — pre-create the bank so it already exists before join
        AtomicInteger createCallCount = new AtomicInteger(0);
        TestSupport.MockStorage storage = new TestSupport.MockStorage() {
            @Override
            public boolean createBank(String name, UUID owner) {
                createCallCount.incrementAndGet();
                return super.createBank(name, owner);
            }
        };
        storage.createBank("Dave", UUID.randomUUID()); // already exists
        TestSupport.injectField(plugin, "storage", storage);

        // Act
        addPlayer("Dave");
        Thread.sleep(200);

        // Assert — createBank was not called again for the pre-existing bank
        assertEquals(1, createCallCount.get(),
                "createBank should not be called again for a bank that already exists");
    }
}
