package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.relation.HasMany;

import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent model for the {@code players} meta table.
 *
 * <p>The primary key is the player's UUID string.  No separate {@code uuid}
 * column is needed; the {@code id} column carries that value.
 */
public class PlayerModel extends Model implements HasFactory {

    /** Repository prefix used by {@link com.github.ezframework.jaloquent.model.TableRegistry}. */
    public static final String PREFIX = "players";

    public PlayerModel(String id) {
        super(id);
        setFillable("name", "displayName");
    }

    /**
     * Jaloquent {@link com.github.ezframework.jaloquent.model.ModelFactory} constructor.
     * Hydrates this model from the map returned by the data store.
     */
    public PlayerModel(String id, Map<String, Object> data) {
        this(id);
        fromMap(data);
    }

    /** Factory compatible with {@link com.github.ezframework.jaloquent.model.ModelFactory}. */
    public static PlayerModel create(UUID uuid, String name, String displayName) {
        PlayerModel m = new PlayerModel(uuid.toString());
        m.set("name",        name);
        m.set("displayName", displayName);
        return m;
    }

    public String getName() {
        return getAs("name", String.class);
    }

    public String getDisplayName() {
        return getAs("displayName", String.class);
    }

    /** All balances across currencies belonging to this player. */
    public HasMany<BalanceModel> balances(ModelRepository<BalanceModel> repo) {
        return hasMany(repo, "uuid");
    }

    /** All recorded transactions belonging to this player. */
    public HasMany<TransactionModel> transactions(ModelRepository<TransactionModel> repo) {
        return hasMany(repo, "uuid");
    }

}
