package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.relation.HasMany;

import java.util.Map;

/**
 * Jaloquent model for a single {@code (name, currency)} bank-balance row.
 *
 * <p>The synthetic primary key is {@code name + "_" + currency}.  This maps
 * directly to the normalized {@code banks} table used by MySQL.
 */
public class BankModel extends Model implements HasFactory {

    /** Repository prefix used by {@link com.github.ezframework.jaloquent.model.TableRegistry}. */
    public static final String PREFIX = "banks";

    public BankModel(String id) {
        super(id);
        setFillable("name", "currency", "balance");
    }

    /**
     * Jaloquent {@link com.github.ezframework.jaloquent.model.ModelFactory} constructor.
     * Hydrates this model from the map returned by the data store.
     */
    public BankModel(String id, Map<String, Object> data) {
        this(id);
        fromMap(data);
    }

    /** Factory compatible with {@link com.github.ezframework.jaloquent.model.ModelFactory}. */
    public static BankModel create(String name, String currency, double balance) {
        BankModel m = new BankModel(idFor(name, currency));
        m.set("name",     name);
        m.set("currency", currency);
        m.set("balance",  balance);
        return m;
    }

    /** Encodes the composite key into a single id value for Jaloquent. */
    public static String idFor(String name, String currency) {
        return name + "_" + currency;
    }

    public String getName() {
        return getAs("name", String.class);
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

    /**
     * All members of this bank.
     * Uses {@code localKey="name"} because {@link BankMemberModel#getBank()}
     * stores the bank <em>name</em>, not the composite {@code name_currency} id.
     */
    public HasMany<BankMemberModel> members(ModelRepository<BankMemberModel> repo) {
        return hasMany(repo, "bank", "name");
    }

}
