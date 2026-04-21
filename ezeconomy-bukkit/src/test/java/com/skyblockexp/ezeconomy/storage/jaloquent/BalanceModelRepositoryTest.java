package com.skyblockexp.ezeconomy.storage.jaloquent;

import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.store.InMemoryDataStore;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BalanceModel} via {@link ModelRepository} backed by
 * {@link InMemoryDataStore} (Jaloquent 1.1.0+).
 *
 * <p>No database or Bukkit environment is required — {@code InMemoryDataStore}
 * holds data in a {@code ConcurrentHashMap} and is designed for exactly this
 * kind of fast, deterministic model-layer testing.
 */
class BalanceModelRepositoryTest {

    private ModelRepository<BalanceModel> repo;

    @BeforeEach
    void setUp() {
        InMemoryDataStore store = new InMemoryDataStore();
        repo = new ModelRepository<>(store, BalanceModel.PREFIX, BalanceModel::new, null);
    }

    @Test
    void save_and_find_returnsStoredBalance() throws Exception {
        UUID uuid = UUID.randomUUID();

        repo.save(BalanceModel.create(uuid, "dollar", 250.0));

        Optional<BalanceModel> found = repo.find(BalanceModel.idFor(uuid, "dollar"));
        assertTrue(found.isPresent(), "Saved model must be retrievable by primary key");
        assertEquals(250.0, found.get().getBalance(), 0.0001);
    }

    @Test
    void save_overwrite_reflectsLatestBalance() throws Exception {
        UUID uuid = UUID.randomUUID();
        repo.save(BalanceModel.create(uuid, "coin", 100.0));
        repo.save(BalanceModel.create(uuid, "coin", 200.0));

        Optional<BalanceModel> found = repo.find(BalanceModel.idFor(uuid, "coin"));
        assertTrue(found.isPresent());
        assertEquals(200.0, found.get().getBalance(), 0.0001,
            "Second save must overwrite the first — balance must reflect the latest write");
    }

    @Test
    void find_unknownId_returnsEmpty() throws Exception {
        Optional<BalanceModel> found = repo.find("00000000-0000-0000-0000-000000000000_dollar");
        assertFalse(found.isPresent(), "Unknown ID must return an empty Optional");
    }

    @Test
    void deleteWhere_byColumnValue_removesAllMatchingRows() throws Exception {
        UUID uuid = UUID.randomUUID();
        repo.save(BalanceModel.create(uuid, "dollar", 50.0));
        repo.save(BalanceModel.create(uuid, "euro",   75.0));

        repo.deleteWhere("uuid", uuid.toString());

        assertFalse(repo.find(BalanceModel.idFor(uuid, "dollar")).isPresent(),
            "'dollar' balance must be gone after deleteWhere on uuid");
        assertFalse(repo.find(BalanceModel.idFor(uuid, "euro")).isPresent(),
            "'euro' balance must be gone after deleteWhere on uuid");
    }

    @Test
    void query_whereEquals_returnsOnlyMatchingRows() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repo.save(BalanceModel.create(a, "dollar", 10.0));
        repo.save(BalanceModel.create(b, "dollar", 20.0));

        List<BalanceModel> results = repo.query(
            BalanceModel.queryBuilder().whereEquals("uuid", a.toString()).build()
        );

        assertEquals(1, results.size(), "Query must return only the row belonging to UUID a");
        assertEquals(a.toString(), results.get(0).getUuid());
        assertEquals(10.0, results.get(0).getBalance(), 0.0001);
    }

    @Test
    void query_noFilter_returnsAllRows() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        repo.save(BalanceModel.create(a, "dollar", 5.0));
        repo.save(BalanceModel.create(b, "dollar", 15.0));

        List<BalanceModel> results = repo.query(BalanceModel.queryBuilder().build());

        assertEquals(2, results.size(), "Unfiltered query must return every saved row");
    }
}
