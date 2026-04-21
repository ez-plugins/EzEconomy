package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Factory is in the same package: BankModelFactory

/**
 * Unit tests for {@link BankModel}.
 *
 * <p>Pure in-memory tests: no database, no Bukkit server.
 */
class BankModelTest {

    // --- idFor ---

    @Test
    void idFor_encodesNameAndCurrency() {
        assertEquals("vaultbank_dollar", BankModel.idFor("vaultbank", "dollar"));
    }

    @Test
    void idFor_differentiatesByCurrency() {
        assertNotEquals(
            BankModel.idFor("bank", "dollar"),
            BankModel.idFor("bank", "euro")
        );
    }

    @Test
    void idFor_differentiatesByName() {
        assertNotEquals(
            BankModel.idFor("bank1", "dollar"),
            BankModel.idFor("bank2", "dollar")
        );
    }

    // --- create ---

    @Test
    void create_setsName() {
        assertEquals("vault", BankModel.create("vault", "dollar", 0.0).getName());
    }

    @Test
    void create_setsCurrency() {
        assertEquals("gold", BankModel.create("bank", "gold", 0.0).getCurrency());
    }

    @Test
    void create_setsBalance() {
        assertEquals(500.0, BankModel.create("bank", "dollar", 500.0).getBalance(), 1e-9);
    }

    @Test
    void create_setsId_toIdFor() {
        BankModel m = BankModel.create("vault", "dollar", 100.0);
        assertEquals(BankModel.idFor("vault", "dollar"), m.getId());
    }

    // --- getBalance / Number coercion ---

    @Test
    void getBalance_whenStoredAsInteger_returnsDoubleValue() {
        BankModel m = new BankModel("someid");
        m.set("balance", 42);    // Integer from JDBC
        assertEquals(42.0, m.getBalance(), 1e-9);
    }

    @Test
    void getBalance_whenStoredAsLong_returnsDoubleValue() {
        BankModel m = new BankModel("someid");
        m.set("balance", 999L);
        assertEquals(999.0, m.getBalance(), 1e-9);
    }

    @Test
    void getBalance_whenNull_returnsZero() {
        BankModel m = new BankModel("someid");
        assertEquals(0.0, m.getBalance(), 1e-9);
    }

    // --- setBalance roundtrip ---

    @Test
    void setBalance_updatesField() {
        BankModel m = new BankModelFactory().make();
        m.setBalance(1234.56);
        assertEquals(1234.56, m.getBalance(), 1e-9);
    }

    // --- PREFIX constant ---

    @Test
    void prefix_matchesExpectedRepositoryKey() {
        assertEquals("banks", BankModel.PREFIX);
    }
}
