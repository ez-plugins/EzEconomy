package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Factory is in the same package: PlayerModelFactory

/**
 * Unit tests for {@link PlayerModel}.
 *
 * <p>Pure in-memory tests: no database, no Bukkit server.
 */
class PlayerModelTest {

    // --- create ---

    @Test
    void create_setsId_toUuidString() {
        UUID uuid = UUID.randomUUID();
        PlayerModel m = PlayerModel.create(uuid, "Alice", "Alice~");
        assertEquals(uuid.toString(), m.getId());
    }

    @Test
    void create_setsName() {
        UUID uuid = UUID.randomUUID();
        PlayerModel m = PlayerModel.create(uuid, "Bob", "BobDisplayName");
        assertEquals("Bob", m.getName());
    }

    @Test
    void create_setsDisplayName() {
        UUID uuid = UUID.randomUUID();
        PlayerModel m = PlayerModel.create(uuid, "Carol", "Carol~Admin~");
        assertEquals("Carol~Admin~", m.getDisplayName());
    }

    @Test
    void create_nameAndDisplayName_canBeSameValue() {
        UUID uuid = UUID.randomUUID();
        PlayerModel m = PlayerModel.create(uuid, "Dave", "Dave");
        assertEquals(m.getName(), m.getDisplayName());
    }

    // --- direct field access ---

    @Test
    void getName_afterSetViaSetMethod_returnsNewValue() {
        PlayerModel m = new PlayerModelFactory().make();
        m.set("name", "Eve2");
        assertEquals("Eve2", m.getName());
    }

    @Test
    void getDisplayName_afterSetViaSetMethod_returnsNewValue() {
        PlayerModel m = new PlayerModelFactory().make();
        m.set("displayName", "FrankUpdated");
        assertEquals("FrankUpdated", m.getDisplayName());
    }

    // --- PREFIX constant ---

    @Test
    void prefix_matchesExpectedRepositoryKey() {
        assertEquals("players", PlayerModel.PREFIX);
    }
}
