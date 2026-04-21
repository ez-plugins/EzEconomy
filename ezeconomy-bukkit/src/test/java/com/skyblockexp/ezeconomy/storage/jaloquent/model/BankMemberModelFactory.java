package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaker.Faker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent {@link Factory} for {@link BankMemberModel}.
 *
 * <p>Produces in-memory {@code BankMemberModel} instances linking a random
 * member UUID to a random bank name.  The {@code owner} flag defaults to
 * {@code false}; use {@link #state(Map)} to set it to {@code true} when
 * the test concerns an owner's perspective.
 *
 * <pre>{@code
 * BankMemberModel member = new BankMemberModelFactory().make();
 *
 * BankMemberModel owner = new BankMemberModelFactory()
 *         .state(Map.of("owner", true))
 *         .make();
 * }</pre>
 */
public class BankMemberModelFactory extends Factory<BankMemberModel> {

    public BankMemberModelFactory() {
        super(BankMemberModel.class);
    }

    @Override
    protected Map<String, Object> definition(Faker faker) {
        String bankName = "bank" + faker.number().numberBetween(1, 99_999);
        String uuid     = UUID.randomUUID().toString();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",    BankMemberModel.idFor(bankName, UUID.fromString(uuid)));
        map.put("bank",  bankName);
        map.put("uuid",  uuid);
        map.put("owner", false);
        return map;
    }
}
