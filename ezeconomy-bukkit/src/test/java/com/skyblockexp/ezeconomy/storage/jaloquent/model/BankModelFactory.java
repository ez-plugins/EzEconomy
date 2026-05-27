package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaker.Faker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jaloquent {@link Factory} for {@link BankModel}.
 *
 * <p>Produces in-memory {@code BankModel} instances with a generated bank name
 * and a default {@code dollar} currency.  Use {@link #state(Map)} to override
 * specific fields.
 *
 * <pre>{@code
 * BankModel any = new BankModelFactory().make();
 *
 * BankModel gold = new BankModelFactory()
 *         .state(Map.of("currency", "gold", "balance", 500.0))
 *         .make();
 * }</pre>
 */
public class BankModelFactory extends Factory<BankModel> {

    public BankModelFactory() {
        super(BankModel.class);
    }

    @Override
    protected Map<String, Object> definition(Faker faker) {
        String name     = "bank" + faker.number().numberBetween(1, 99_999);
        String currency = "dollar";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",       BankModel.idFor(name, currency));
        map.put("name",     name);
        map.put("currency", currency);
        map.put("balance",  faker.number().random(0.0, 10_000.0));
        return map;
    }
}
