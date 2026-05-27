package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaker.Faker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent {@link Factory} for {@link BalanceModel}.
 *
 * <p>Produces in-memory {@code BalanceModel} instances with randomised but
 * structurally valid defaults.  Use {@link #state(Map)} to pin specific field
 * values when a test requires them.
 *
 * <pre>{@code
 * // Default random instance
 * BalanceModel m = new BalanceModelFactory().make();
 *
 * // Pin a specific balance
 * BalanceModel m = new BalanceModelFactory()
 *         .state(Map.of("balance", 99.5))
 *         .make();
 * }</pre>
 */
public class BalanceModelFactory extends Factory<BalanceModel> {

    public BalanceModelFactory() {
        super(BalanceModel.class);
    }

    @Override
    protected Map<String, Object> definition(Faker faker) {
        String uuid     = UUID.randomUUID().toString();
        String currency = "dollar";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",       BalanceModel.idFor(UUID.fromString(uuid), currency));
        map.put("uuid",     uuid);
        map.put("currency", currency);
        map.put("balance",  faker.number().random(0.0, 1_000.0));
        return map;
    }
}
