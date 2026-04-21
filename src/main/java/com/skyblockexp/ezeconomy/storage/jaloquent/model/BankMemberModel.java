package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.relation.BelongsTo;

import java.util.Map;
import java.util.UUID;

/**
 * Jaloquent model for a single {@code bank_members} row linking a player to a bank.
 *
 * <p>The synthetic primary key is {@code bank + "_" + uuid}.
 */
public class BankMemberModel extends Model implements HasFactory {

    /** Repository prefix used by {@link com.github.ezframework.jaloquent.model.TableRegistry}. */
    public static final String PREFIX = "bank_members";

    public BankMemberModel(String id) {
        super(id);
        setFillable("bank", "uuid", "owner");
    }

    /**
     * Jaloquent {@link com.github.ezframework.jaloquent.model.ModelFactory} constructor.
     * Hydrates this model from the map returned by the data store.
     */
    public BankMemberModel(String id, Map<String, Object> data) {
        this(id);
        fromMap(data);
    }

    /** Factory compatible with {@link com.github.ezframework.jaloquent.model.ModelFactory}. */
    public static BankMemberModel create(String bankName, UUID uuid, boolean owner) {
        BankMemberModel m = new BankMemberModel(idFor(bankName, uuid));
        m.set("bank",  bankName);
        m.set("uuid",  uuid.toString());
        m.set("owner", owner);
        return m;
    }

    /** Encodes the composite key into a single id value for Jaloquent. */
    public static String idFor(String bankName, UUID uuid) {
        return bankName + "_" + uuid.toString();
    }

    public String getBank() {
        return getAs("bank", String.class);
    }

    public String getMemberUuid() {
        return getAs("uuid", String.class);
    }

    public boolean isOwner() {
        Object v = get("owner");
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return Boolean.parseBoolean(v.toString());
    }

    /** The player linked to this membership record. */
    public BelongsTo<PlayerModel> player(ModelRepository<PlayerModel> repo) {
        return belongsTo(repo, "uuid");
    }

}
