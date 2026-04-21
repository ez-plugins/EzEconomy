package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaker.Faker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent {@link Factory} for {@link TransactionModel}.
 *
 * <p>Produces in-memory {@code TransactionModel} instances with a fresh random
 * UUID primary key, a random player UUID, a {@code dollar} currency, a random
 * amount and a random timestamp.  Use {@link #state(Map)} to pin specific
 * field values.
 *
 * <pre>{@code
 * TransactionModel tx = new TransactionModelFactory().make();
 *
 * TransactionModel specific = new TransactionModelFactory()
 *         .state(Map.of("amount", 75.5, "currency", "gold"))
 *         .make();
 * }</pre>
 */
public class TransactionModelFactory extends Factory<TransactionModel> {

    public TransactionModelFactory() {
        super(TransactionModel.class);
    }

    @Override
    protected Map<String, Object> definition(Faker faker) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",        UUID.randomUUID().toString());
        map.put("uuid",      UUID.randomUUID().toString());
        map.put("currency",  "dollar");
        map.put("amount",    faker.number().random(0.01, 500.0));
        map.put("timestamp", faker.number().randomLong(0L, System.currentTimeMillis()));
        return map;
    }
}
