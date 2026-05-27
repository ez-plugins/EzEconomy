package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TransactionModel}.
 *
 * <p>Pure in-memory tests: no database, no Bukkit server.
 */
class TransactionModelTest {

    // --- fromTransaction field mapping ---

    @Test
    void fromTransaction_setsPlayerUuid() {
        UUID uuid = UUID.randomUUID();
        Transaction tx = new Transaction(uuid, "dollar", 100.0, 123456789L);
        assertEquals(uuid.toString(), TransactionModel.fromTransaction(tx).getPlayerUuid());
    }

    @Test
    void fromTransaction_setsCurrency() {
        Transaction tx = new Transaction(UUID.randomUUID(), "euro", 50.0, 0L);
        assertEquals("euro", TransactionModel.fromTransaction(tx).getCurrency());
    }

    @Test
    void fromTransaction_setsAmount() {
        Transaction tx = new Transaction(UUID.randomUUID(), "dollar", 75.5, 0L);
        assertEquals(75.5, TransactionModel.fromTransaction(tx).getAmount(), 1e-9);
    }

    @Test
    void fromTransaction_setsTimestamp() {
        long ts = 987654321L;
        Transaction tx = new Transaction(UUID.randomUUID(), "dollar", 1.0, ts);
        assertEquals(ts, TransactionModel.fromTransaction(tx).getTimestamp());
    }

    // --- unique id per call ---

    @Test
    void fromTransaction_twoCallsSameTx_produceDifferentIds() {
        Transaction tx = new Transaction(UUID.randomUUID(), "dollar", 1.0, 0L);
        String id1 = TransactionModel.fromTransaction(tx).getId();
        String id2 = TransactionModel.fromTransaction(tx).getId();
        assertNotEquals(id1, id2,
            "Each fromTransaction call must generate a fresh random UUID as id");
    }

    // --- getAmount Number coercion ---

    @Test
    void getAmount_whenStoredAsInteger_returnsDouble() {
        TransactionModel m = new TransactionModel(UUID.randomUUID().toString());
        m.set("amount", 10);
        assertEquals(10.0, m.getAmount(), 1e-9);
    }

    @Test
    void getAmount_whenNull_returnsZero() {
        TransactionModel m = new TransactionModel(UUID.randomUUID().toString());
        assertEquals(0.0, m.getAmount(), 1e-9);
    }

    // --- PREFIX constant ---

    @Test
    void prefix_matchesExpectedRepositoryKey() {
        assertEquals("transactions", TransactionModel.PREFIX);
    }
}
