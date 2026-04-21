package com.skyblockexp.ezeconomy.storage.jaloquent.model;

import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;

import java.util.Map;

/**
 * Jaloquent model for a single cache row in {@code ezeconomy_cache}.
 *
 * <p>The primary key is {@code k} (the cache key string). Columns are:
 * <ul>
 *   <li>{@code k}         – VARCHAR(191) PRIMARY KEY</li>
 *   <li>{@code v}         – TEXT (serialised value)</li>
 *   <li>{@code expiresAt} – BIGINT epoch-ms; 0 means never expires</li>
 * </ul>
 */
public class CacheEntryModel extends Model implements HasFactory {

    /** Repository prefix used by {@link com.github.ezframework.jaloquent.model.TableRegistry}. */
    public static final String PREFIX = "cache";

    public CacheEntryModel(String id) {
        super(id);
        setFillable("k", "v", "expiresAt");
    }

    /**
     * Jaloquent {@link com.github.ezframework.jaloquent.model.ModelFactory} constructor.
     * Hydrates this model from the map returned by the data store.
     */
    public CacheEntryModel(String id, Map<String, Object> data) {
        this(id);
        fromMap(data);
    }

    /** Factory method that creates a fully-populated cache entry. */
    public static CacheEntryModel create(String key, String value, long expiresAt) {
        CacheEntryModel m = new CacheEntryModel(key);
        m.set("k", key);
        m.set("v", value);
        m.set("expiresAt", expiresAt);
        return m;
    }

    public String getV() {
        return getAs("v", String.class);
    }

    public long getExpiresAt() {
        Object val = get("expiresAt");
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        return 0L;
    }
}
