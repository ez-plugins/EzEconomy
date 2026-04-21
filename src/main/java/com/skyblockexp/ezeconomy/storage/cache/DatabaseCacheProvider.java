package com.skyblockexp.ezeconomy.storage.cache;

import com.skyblockexp.ezeconomy.cache.CacheProvider;
import com.skyblockexp.ezeconomy.cache.ExpiringCache;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzJdbcStore;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzTableRegistry;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.CacheEntryModel;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.Optional;

/**
 * Database-backed cache provider using MySQL (configured via plugin config).
 * Creates a simple {@code ezeconomy_cache} table if missing, then uses
 * Jaloquent for all subsequent read/write operations.
 */
public class DatabaseCacheProvider<K, V> implements CacheProvider<K, V> {
    private EzJdbcStore jdbcStore;
    private ModelRepository<CacheEntryModel> cacheRepo;

    public DatabaseCacheProvider() {
        try {
            EzEconomyPlugin plugin = EzEconomyPlugin.getInstance();
            if (plugin == null) return;
            String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("mysql.port", 3306);
            String database = plugin.getConfig().getString("mysql.database", "ezeconomy");
            String user = plugin.getConfig().getString("mysql.username", "root");
            String pass = plugin.getConfig().getString("mysql.password", "");
            Connection conn = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, user, pass);
            this.jdbcStore = new EzJdbcStore(conn);
            EzTableRegistry.registerCache("ezeconomy_cache");
            jdbcStore.executeUpdate(
                QueryBuilder.createTable("ezeconomy_cache").ifNotExists()
                    .column("k", "VARCHAR(191)").primaryKey("k")
                    .column("v", "TEXT").column("expiresAt", "BIGINT")
                    .build(SqlDialect.MYSQL).getSql(),
                Collections.emptyList());
            this.cacheRepo = new ModelRepository<>(jdbcStore, "cache", CacheEntryModel::new, SqlDialect.MYSQL);
        } catch (Exception ex) {
            this.jdbcStore = null;
            this.cacheRepo = null;
        }
    }

    @Override
    public ExpiringCache.Entry<V> getEntry(K key) {
        if (cacheRepo == null) return null;
        try {
            Optional<CacheEntryModel> opt = cacheRepo.find(String.valueOf(key));
            if (!opt.isPresent()) return null;
            CacheEntryModel entry = opt.get();
            long expiresAt = entry.getExpiresAt();
            if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) return null;
            return new ExpiringCache.Entry<>((V) entry.getV(), expiresAt);
        } catch (StorageException ex) {
            return null;
        }
    }

    @Override
    public void put(K key, V value, long ttlMs) {
        if (cacheRepo == null) return;
        long expiresAt = ttlMs <= 0 ? 0L : System.currentTimeMillis() + ttlMs;
        try {
            cacheRepo.save(CacheEntryModel.create(String.valueOf(key), String.valueOf(value), expiresAt));
        } catch (StorageException ignored) {}
    }

    @Override
    public void remove(K key) {
        if (cacheRepo == null) return;
        try {
            cacheRepo.delete(String.valueOf(key));
        } catch (StorageException ignored) {}
    }
}

