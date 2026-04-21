package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.relation.BelongsTo;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;

import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent model for a single row in the {@code transactions} table.
 *
 * <p>A new random UUID is generated as the primary key on each insertion so
 * that multiple transactions for the same player and currency are preserved.
 */
public class TransactionModel extends Model implements HasFactory {

    /** Repository prefix used by {@link com.github.ezframework.jaloquent.model.TableRegistry}. */
    public static final String PREFIX = "transactions";

    public TransactionModel(String id) {
        super(id);
        setFillable("uuid", "currency", "amount", "timestamp");
    }

    /**
     * Jaloquent {@link com.github.ezframework.jaloquent.model.ModelFactory} constructor.
     * Hydrates this model from the map returned by the data store.
     */
    public TransactionModel(String id, Map<String, Object> data) {
        this(id);
        fromMap(data);
    }

    /** Factory compatible with {@link com.github.ezframework.jaloquent.model.ModelFactory}. */
    public static TransactionModel fromTransaction(Transaction tx) {
        TransactionModel m = new TransactionModel(UUID.randomUUID().toString());
        m.set("uuid",      tx.getUuid().toString());
        m.set("currency",  tx.getCurrency());
        m.set("amount",    tx.getAmount());
        m.set("timestamp", tx.getTimestamp());
        return m;
    }

    public String getPlayerUuid() {
        return getAs("uuid", String.class);
    }

    public String getCurrency() {
        return getAs("currency", String.class);
    }

    public double getAmount() {
        Object v = get("amount");
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    public long getTimestamp() {
        return getAs("timestamp", Long.class, 0L);
    }

    /** The player who triggered this transaction. */
    public BelongsTo<PlayerModel> player(ModelRepository<PlayerModel> repo) {
        return belongsTo(repo, "uuid");
    }

}
