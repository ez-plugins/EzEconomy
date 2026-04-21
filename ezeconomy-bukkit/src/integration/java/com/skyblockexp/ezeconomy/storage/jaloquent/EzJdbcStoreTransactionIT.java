package com.skyblockexp.ezeconomy.storage.jaloquent;

import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzTableRegistry;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel;
import com.skyblockexp.ezeconomy.test.DbTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link EzJdbcStore}'s {@code TransactionalJdbcStore}
 * implementation (added in Jaloquent 1.1.0).
 *
 * <p>Uses an in-memory H2 database (MySQL-compat mode) to verify that
 * {@code beginTransaction}, {@code commitTransaction}, and
 * {@code rollbackTransaction} produce the correct JDBC-level durability
 * guarantees without requiring a running MySQL server.
 *
 * <p>Naming follows the project's integration-test convention ({@code IT} suffix).
 */
public class EzJdbcStoreTransactionIT {

    private Connection conn;
    private EzJdbcStore store;
    private ModelRepository<BalanceModel> repo;

    @BeforeEach
    void setUp() throws Exception {
        conn = DbTestHelper.createH2MemoryMysql();
        try (Statement s = conn.createStatement()) {
            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS balances " +
                "(id VARCHAR(69) NOT NULL, uuid VARCHAR(36), currency VARCHAR(32), " +
                " balance DOUBLE, PRIMARY KEY (id))"
            );
        }
        // Jaloquent's TableRegistry is JVM-static.  Other tests (e.g.
        // EzTableRegistryTest) may have registered "balances" under a
        // different physical name.  Re-register with the exact table name
        // this test creates so that save/find SQL targets the right table.
        EzTableRegistry.registerAll("balances", "players", "banks", "bank_members", "transactions");
        store = new EzJdbcStore(conn);
        repo  = new ModelRepository<>(store, BalanceModel.PREFIX, BalanceModel::new, SqlDialect.MYSQL);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // --- auto-commit state ---

    @Test
    void beginTransaction_setsConnectionAutoCommitToFalse() throws Exception {
        assertTrue(conn.getAutoCommit(), "Auto-commit must be true before any transaction");

        store.beginTransaction();

        assertFalse(conn.getAutoCommit(), "Auto-commit must be false while a transaction is open");
        store.rollbackTransaction(); // cleanup
    }

    @Test
    void commitTransaction_restoresAutoCommit() throws Exception {
        store.beginTransaction();
        repo.save(BalanceModel.create(UUID.randomUUID(), "dollar", 1.0));
        store.commitTransaction();

        assertTrue(conn.getAutoCommit(), "Auto-commit must be restored to true after commit");
    }

    @Test
    void rollbackTransaction_restoresAutoCommit() throws Exception {
        store.beginTransaction();
        store.rollbackTransaction();

        assertTrue(conn.getAutoCommit(), "Auto-commit must be restored to true after rollback");
    }

    // --- durability guarantees ---

    @Test
    void commitTransaction_persistsWritesMadeInTransaction() throws Exception {
        UUID uuid = UUID.randomUUID();

        store.beginTransaction();
        repo.save(BalanceModel.create(uuid, "dollar", 999.0));
        store.commitTransaction();

        Optional<BalanceModel> found = repo.find(BalanceModel.idFor(uuid, "dollar"));
        assertTrue(found.isPresent(), "Committed row must be visible after commit");
        assertEquals(999.0, found.get().getBalance(), 0.0001);
    }

    @Test
    void rollbackTransaction_revertsWritesMadeInTransaction() throws Exception {
        UUID uuid = UUID.randomUUID();

        store.beginTransaction();
        repo.save(BalanceModel.create(uuid, "dollar", 500.0));
        store.rollbackTransaction();

        Optional<BalanceModel> found = repo.find(BalanceModel.idFor(uuid, "dollar"));
        assertFalse(found.isPresent(), "Rolled-back write must not be visible after rollback");
    }

    @Test
    void rollbackTransaction_doesNotAffectPriorCommittedData() throws Exception {
        UUID uuid = UUID.randomUUID();

        // First transaction — committed
        store.beginTransaction();
        repo.save(BalanceModel.create(uuid, "dollar", 100.0));
        store.commitTransaction();

        // Second transaction — rolled back
        store.beginTransaction();
        repo.save(BalanceModel.create(uuid, "dollar", 999.0));
        store.rollbackTransaction();

        Optional<BalanceModel> found = repo.find(BalanceModel.idFor(uuid, "dollar"));
        assertTrue(found.isPresent(), "Prior committed row must still exist after a later rollback");
        assertEquals(100.0, found.get().getBalance(), 0.0001,
            "Balance must remain at the last committed value, not the rolled-back update");
    }
}
