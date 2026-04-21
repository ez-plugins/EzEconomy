package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Factory is in the same package: BalanceModelFactory

/**
 * Unit tests for {@link BalanceModel}.
 *
 * <p>These are pure in-memory tests: no database, no Bukkit server.
 */
class BalanceModelTest {

    private static final UUID FIXED_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    // --- idFor ---

    @Test
    void idFor_encodesUuidAndCurrency() {
        assertEquals(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa_dollar",
            BalanceModel.idFor(FIXED_UUID, "dollar")
        );
    }

    @Test
    void idFor_differentiatesByCurrency() {
        assertNotEquals(
            BalanceModel.idFor(FIXED_UUID, "dollar"),
            BalanceModel.idFor(FIXED_UUID, "euro")
        );
    }

    // --- create ---

    @Test
    void create_setsUuid() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid.toString(), BalanceModel.create(uuid, "dollar", 10.0).getUuid());
    }

    @Test
    void create_setsCurrency() {
        assertEquals("gold", BalanceModel.create(UUID.randomUUID(), "gold", 0.0).getCurrency());
    }

    @Test
    void create_setsBalance() {
        double balance = 99.5;
        assertEquals(balance, BalanceModel.create(UUID.randomUUID(), "dollar", balance).getBalance(), 1e-9);
    }

    @Test
    void create_setsId_toIdFor() {
        UUID uuid = UUID.randomUUID();
        BalanceModel m = BalanceModel.create(uuid, "dollar", 0.0);
        assertEquals(BalanceModel.idFor(uuid, "dollar"), m.getId());
    }

    // --- getBalance / Number coercion ---

    @Test
    void getBalance_whenStoredAsInteger_returnsDoubleValue() {
        BalanceModel m = new BalanceModel(BalanceModel.idFor(UUID.randomUUID(), "dollar"));
        m.set("balance", 42);          // Integer stored (e.g. from H2 / SQLite JDBC)
        assertEquals(42.0, m.getBalance(), 1e-9);
    }

    @Test
    void getBalance_whenStoredAsLong_returnsDoubleValue() {
        BalanceModel m = new BalanceModel(BalanceModel.idFor(UUID.randomUUID(), "dollar"));
        m.set("balance", 200L);
        assertEquals(200.0, m.getBalance(), 1e-9);
    }

    @Test
    void getBalance_whenNull_returnsZero() {
        BalanceModel m = new BalanceModel(BalanceModel.idFor(UUID.randomUUID(), "dollar"));
        assertEquals(0.0, m.getBalance(), 1e-9);
    }

    // --- setBalance roundtrip ---

    @Test
    void setBalance_updatesBalanceField() {
        BalanceModel m = new BalanceModelFactory().make();
        m.setBalance(250.75);
        assertEquals(250.75, m.getBalance(), 1e-9);
    }

    // --- PREFIX constant ---

    @Test
    void prefix_matchesExpectedRepositoryKey() {
        assertEquals("balances", BalanceModel.PREFIX);
    }
}
