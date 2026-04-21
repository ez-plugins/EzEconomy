package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaker.Faker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent {@link Factory} for {@link PlayerModel}.
 *
 * <p>Produces in-memory {@code PlayerModel} instances whose primary key is a
 * random UUID string and whose name/displayName fields are populated by
 * {@link Faker}.
 *
 * <pre>{@code
 * PlayerModel any = new PlayerModelFactory().make();
 *
 * PlayerModel named = new PlayerModelFactory()
 *         .state(Map.of("name", "Alice", "displayName", "Alice~"))
 *         .make();
 * }</pre>
 */
public class PlayerModelFactory extends Factory<PlayerModel> {

    public PlayerModelFactory() {
        super(PlayerModel.class);
    }

    @Override
    protected Map<String, Object> definition(Faker faker) {
        String uuid = UUID.randomUUID().toString();
        String name = faker.name().firstName();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",          uuid);
        map.put("name",        name);
        map.put("displayName", name);
        return map;
    }
}
