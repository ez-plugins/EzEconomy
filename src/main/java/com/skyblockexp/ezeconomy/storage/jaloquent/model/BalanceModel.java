package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.relation.BelongsTo;

import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent model for a single {@code (uuid, currency)} balance row.
 *
 * <p>The synthetic primary key is {@code uuid + "_" + currency}, which keeps
 * the single-column {@code id} PK that Jaloquent requires while preserving
 * the original composite-key semantics.
 */
public class BalanceModel extends Model implements HasFactory {

    /** Repository prefix used by {@link com.github.ezframework.jaloquent.model.TableRegistry}. */
    public static final String PREFIX = "balances";

    public BalanceModel(String id) {
        super(id);
        setFillable("uuid", "currency", "balance");
    }

    /**
     * Jaloquent {@link com.github.ezframework.jaloquent.model.ModelFactory} constructor.
     * Hydrates this model from the map returned by the data store.
     */
    public BalanceModel(String id, Map<String, Object> data) {
        this(id);
        fromMap(data);
    }

    /** Factory compatible with {@link com.github.ezframework.jaloquent.model.ModelFactory}. */
    public static BalanceModel create(UUID uuid, String currency, double balance) {
        BalanceModel m = new BalanceModel(idFor(uuid, currency));
        m.set("uuid",     uuid.toString());
        m.set("currency", currency);
        m.set("balance",  balance);
        return m;
    }

    /** Encodes the composite key into a single id value for Jaloquent. */
    public static String idFor(UUID uuid, String currency) {
        return uuid.toString() + "_" + currency;
    }

    public String getUuid() {
        return getAs("uuid", String.class);
    }

    public String getCurrency() {
        return getAs("currency", String.class);
    }

    public double getBalance() {
        Object v = get("balance");
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    public void setBalance(double balance) {
        set("balance", balance);
    }

    /** The player who owns this balance. */
    public BelongsTo<PlayerModel> player(ModelRepository<PlayerModel> repo) {
        return belongsTo(repo, "uuid");
    }

}
