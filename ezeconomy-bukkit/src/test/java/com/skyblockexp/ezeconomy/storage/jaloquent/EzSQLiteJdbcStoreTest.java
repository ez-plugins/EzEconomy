package com.skyblockexp.ezeconomy.storage.jaloquent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the SQL dialect translation in {@link EzSQLiteJdbcStore#transformSql}.
 *
 * <p>The store is instantiated with a {@code null} connection — safe here
 * because {@code transformSql} is a pure string transformation that never
 * touches the underlying connection.
 *
 * <p>{@code transformSql} is {@code protected} so the test lives in the same
 * package, granting direct access without reflection.
 */
class EzSQLiteJdbcStoreTest {

    private final EzSQLiteJdbcStore store = new EzSQLiteJdbcStore(null);

    @Test
    void transformSql_selectStatement_returnedUnchanged() {
        String sql = "SELECT * FROM balances WHERE id=?";
        assertEquals(sql, store.transformSql(sql));
    }

    @Test
    void transformSql_insertWithoutOnDuplicate_returnedUnchanged() {
        String sql = "INSERT INTO balances (id, uuid, currency, balance) VALUES (?, ?, ?, ?)";
        assertEquals(sql, store.transformSql(sql));
    }

    @Test
    void transformSql_singleColumnUpsert_rewrittenToOnConflict() {
        String input  = "INSERT INTO balances (id, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance=VALUES(balance)";
        String result = store.transformSql(input);

        assertTrue(result.contains("ON CONFLICT(id) DO UPDATE SET"),
            "Expected ON CONFLICT clause; got: " + result);
        assertTrue(result.contains("balance=excluded.balance"),
            "Expected excluded.balance assignment; got: " + result);
        assertFalse(result.contains("ON DUPLICATE KEY UPDATE"),
            "MySQL syntax must be removed; got: " + result);
    }

    @Test
    void transformSql_multiColumnUpsert_rewritesAllAssignments() {
        String input  = "INSERT INTO balances (id, uuid, currency, balance) VALUES (?, ?, ?, ?) "
                      + "ON DUPLICATE KEY UPDATE uuid=VALUES(uuid),currency=VALUES(currency),balance=VALUES(balance)";
        String result = store.transformSql(input);

        assertTrue(result.contains("uuid=excluded.uuid"),     "uuid assignment missing; got: " + result);
        assertTrue(result.contains("currency=excluded.currency"), "currency assignment missing; got: " + result);
        assertTrue(result.contains("balance=excluded.balance"),   "balance assignment missing; got: " + result);
        assertFalse(result.contains("VALUES("), "VALUES() syntax must be gone; got: " + result);
    }

    @Test
    void transformSql_caseInsensitiveOnDuplicateKeyword_isRewritten() {
        String input  = "INSERT INTO t (id, x) VALUES (?, ?) on duplicate key update x=VALUES(x)";
        String result = store.transformSql(input);

        assertTrue(result.contains("ON CONFLICT(id) DO UPDATE SET x=excluded.x"),
            "Lowercase ON DUPLICATE KEY UPDATE must be handled; got: " + result);
    }
}
