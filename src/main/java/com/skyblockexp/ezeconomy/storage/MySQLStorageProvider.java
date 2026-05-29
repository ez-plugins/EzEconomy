package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.api.storage.EconomyMutationResult;
import org.bukkit.configuration.file.YamlConfiguration;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageInitException;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageLoadException;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageSaveException;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzJdbcStore;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzTableRegistry;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BankMemberModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BankModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.PlayerModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.TransactionModel;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import com.skyblockexp.ezeconomy.api.events.BankPreTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.BankPostTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.TransactionType;
import com.skyblockexp.ezeconomy.util.EventDispatcher;
import com.skyblockexp.ezeconomy.cache.CacheManager;
import com.skyblockexp.ezeconomy.cache.CacheProvider;
import com.skyblockexp.ezeconomy.cache.ExpiringCache;

/**
 * MySQL implementation of the StorageProvider interface for EzEconomy.
 * Handles player and bank balances using a MySQL database.
 * Thread-safe and ready for open-source use.
 */
public class MySQLStorageProvider implements StorageProvider {
    private final EzEconomyPlugin plugin;
    private Connection connection;
    private String table;
    private final Object lock = new Object();
    private final YamlConfiguration dbConfig;

    // Jaloquent repositories
    private EzJdbcStore jdbcStore;
    private ModelRepository<BalanceModel>    balanceRepo;
    private ModelRepository<PlayerModel>     playerRepo;
    private ModelRepository<BankModel>       bankRepo;
    private ModelRepository<BankMemberModel> bankMemberRepo;
    private ModelRepository<TransactionModel> transactionRepo;
    private final boolean balanceCacheEnabled;
    private final long balanceCacheTtlMs;
    private volatile boolean schemaInitialized;
    private PreparedStatement psDepositUpsert;
    private PreparedStatement psWithdrawIfEnough;
    private PreparedStatement psSelectBalanceById;

    /**
     * Constructs a MySQLStorageProvider with the given plugin and configuration.
     * @param plugin EzEconomy plugin instance
     * @param dbConfig YAML configuration for MySQL
     */
    public MySQLStorageProvider(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
        if (dbConfig == null) throw new IllegalArgumentException("MySQL config is missing!");
        this.table = dbConfig.getString("mysql.table", "balances");
        this.balanceCacheEnabled = plugin.getConfig().getBoolean("performance.balance-cache.enabled", true);
        this.balanceCacheTtlMs = plugin.getConfig().getLong("performance.balance-cache.ttl-ms", 2500L);
    }

    @Override
    public void init() throws StorageInitException {
        // Create tables/schema if needed
        if (connection == null) {
            // Establish a temporary connection for schema creation
            String host = dbConfig.getString("mysql.host");
            int port = dbConfig.getInt("mysql.port");
            String database = dbConfig.getString("mysql.database");
            String username = dbConfig.getString("mysql.username");
            String password = dbConfig.getString("mysql.password");
            try (Connection tempConn = DriverManager.getConnection(
                    buildJdbcUrl(host, port, database),
                    username, password)) {
                EzJdbcStore tempJdbc = new EzJdbcStore(tempConn);
                java.util.List<Object> noParams = java.util.Collections.emptyList();
                tempJdbc.executeUpdate(QueryBuilder.createTable(table).ifNotExists()
                        .column("id", "VARCHAR(69)").primaryKey("id")
                        .column("uuid", "VARCHAR(36)").column("currency", "VARCHAR(32)").column("balance", "DOUBLE")
                        .build(SqlDialect.MYSQL).getSql(), noParams);
                tempJdbc.executeUpdate(QueryBuilder.createTable("banks").ifNotExists()
                        .column("id", "VARCHAR(97)").primaryKey("id")
                        .column("name", "VARCHAR(64)").column("currency", "VARCHAR(32)").column("balance", "DOUBLE")
                        .build(SqlDialect.MYSQL).getSql(), noParams);
                tempJdbc.executeUpdate(QueryBuilder.createTable("bank_members").ifNotExists()
                        .column("id", "VARCHAR(101)").primaryKey("id")
                        .column("bank", "VARCHAR(64)").column("uuid", "VARCHAR(36)").column("owner", "BOOLEAN")
                        .build(SqlDialect.MYSQL).getSql(), noParams);
                tempJdbc.executeUpdate(QueryBuilder.createTable("players").ifNotExists()
                        .column("id", "VARCHAR(36)").primaryKey("id")
                        .column("name", "VARCHAR(64)").column("displayName", "VARCHAR(128)")
                        .build(SqlDialect.MYSQL).getSql(), noParams);
                tempJdbc.executeUpdate(QueryBuilder.createTable("transactions").ifNotExists()
                        .column("id", "VARCHAR(36)").primaryKey("id")
                        .column("uuid", "VARCHAR(36)").column("currency", "VARCHAR(32)")
                        .column("amount", "DOUBLE").column("timestamp", "BIGINT")
                        .build(SqlDialect.MYSQL).getSql(), noParams);
                tempJdbc.executeUpdate("CREATE TABLE IF NOT EXISTS ezeconomy_pending_notifications ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "uuid VARCHAR(36) NOT NULL, "
                        + "message TEXT NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "INDEX idx_pending_uuid (uuid)"
                        + ")", noParams);
                schemaInitialized = true;
            } catch (Exception e) {
                schemaInitialized = false;
                plugin.getLogger().warning("MySQL schema init failed: " + e.getMessage());
                throw new StorageInitException("Failed to initialize MySQL schema", e);
            }
        } else {
            schemaInitialized = true;
        }
    }

    @Override
    public void load() throws StorageLoadException {
        // Establish connection only
        String host = dbConfig.getString("mysql.host");
        int port = dbConfig.getInt("mysql.port");
        String database = dbConfig.getString("mysql.database");
        String username = dbConfig.getString("mysql.username");
        String password = dbConfig.getString("mysql.password");
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = DriverManager.getConnection(
                buildJdbcUrl(host, port, database),
                username, password);
            initRepositories();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL connection failed: " + e.getMessage());
            throw new StorageLoadException("Failed to connect to MySQL", e);
        }
    }

    @Override
    public void save() throws StorageSaveException {
        // No in-memory cache, so nothing to save
    }

    /**
     * Initialises Jaloquent stores and repositories after a connection is established.
     * Called automatically from {@link #load()}.  May also be called in tests that
     * inject a connection via reflection.
     */
    public void initRepositories() {
        EzTableRegistry.registerAll(table, "players", "banks", "bank_members", "transactions");
        jdbcStore       = new EzJdbcStore(connection);
        balanceRepo     = new ModelRepository<>(jdbcStore, BalanceModel.PREFIX,    BalanceModel::new,    SqlDialect.MYSQL);
        playerRepo      = new ModelRepository<>(jdbcStore, PlayerModel.PREFIX,     PlayerModel::new,     SqlDialect.MYSQL);
        bankRepo        = new ModelRepository<>(jdbcStore, BankModel.PREFIX,       BankModel::new,       SqlDialect.MYSQL);
        bankMemberRepo  = new ModelRepository<>(jdbcStore, BankMemberModel.PREFIX, BankMemberModel::new, SqlDialect.MYSQL);
        transactionRepo = new ModelRepository<>(jdbcStore, TransactionModel.PREFIX,TransactionModel::new,SqlDialect.MYSQL);
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private String buildJdbcUrl(String host, int port, String database) {
        String params = dbConfig.getString("mysql.jdbc-params",
                "useSSL=false&serverTimezone=UTC&cachePrepStmts=true&prepStmtCacheSize=256&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true&elideSetAutoCommits=true&maintainTimeStats=false&useLocalSessionState=true&rewriteBatchedStatements=true&cacheResultSetMetadata=true&cacheServerConfiguration=true");
        if (params == null || params.trim().isEmpty()) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
    }

    private String balanceCacheKey(UUID uuid, String currency) {
        return "bal:" + uuid + ":" + currency;
    }

    private String bankBalanceCacheKey(String name, String currency) {
        return "bank:" + name + ":" + currency;
    }

    private Double getCached(String key) {
        if (!balanceCacheEnabled) return null;
        try {
            CacheProvider<String, Double> cache = CacheManager.getProvider();
            ExpiringCache.Entry<Double> entry = cache.getEntry(key);
            if (entry == null || entry.value == null) return null;
            if (entry.expiresAt < System.currentTimeMillis()) return null;
            return entry.value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void putCached(String key, double value) {
        if (!balanceCacheEnabled) return;
        try {
            CacheProvider<String, Double> cache = CacheManager.getProvider();
            cache.put(key, value, balanceCacheTtlMs);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public java.util.List<Transaction> getTransactions(java.util.UUID uuid, String currency) {
        java.util.List<Transaction> transactions = new java.util.ArrayList<>();
        synchronized (lock) {
            try {
                java.util.List<TransactionModel> rows = transactionRepo.query(
                        TransactionModel.queryBuilder()
                                .whereEquals("uuid", uuid.toString())
                                .whereEquals("currency", currency)
                                .orderBy("timestamp", false)
                                .build());
                for (TransactionModel tm : rows) {
                    transactions.add(new Transaction(uuid, currency, tm.getAmount(), tm.getTimestamp()));
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL getTransactions failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
        return transactions;
    }

    /**
     * Gets the balance for a player and currency.
     */
    @Override
    public double getBalance(UUID uuid, String currency) {
        Double cached = getCached(balanceCacheKey(uuid, currency));
        if (cached != null) return cached.doubleValue();
        synchronized (lock) {
            try {
                double value = balanceRepo.find(BalanceModel.idFor(uuid, currency))
                       .map(BalanceModel::getBalance).orElse(0.0);
                putCached(balanceCacheKey(uuid, currency), value);
                return value;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL getBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
            return 0.0;
        }
    }

    @Override
    public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (playerRepo == null) {
            org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
        }
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        PlayerModel pm = playerRepo.find(uuid.toString()).orElse(null);
                        if (pm != null) {
                            String pname = pm.getName() != null ? pm.getName() : uuid.toString();
                            String pdisplay = pm.getDisplayName() != null ? pm.getDisplayName() : pname;
                            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, pname, pdisplay);
                        }
                    } catch (StorageException ignored) {}
                    org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                    String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                    return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            try {
                PlayerModel pm = playerRepo.find(uuid.toString()).orElse(null);
                if (pm != null) {
                    String pname = pm.getName() != null ? pm.getName() : uuid.toString();
                    String pdisplay = pm.getDisplayName() != null ? pm.getDisplayName() : pname;
                    return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, pname, pdisplay);
                }
            } catch (StorageException ignored) {}
            org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
        }
    }
    @Override
    public boolean playerExists(UUID uuid) {
        synchronized (lock) {
            try {
                return !balanceRepo.query(BalanceModel.queryBuilder().whereEquals("uuid", uuid.toString()).limit(1).build()).isEmpty();
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL playerExists failed for " + uuid + ": " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        balanceRepo.save(BalanceModel.create(uuid, currency, amount));
                        putCached(balanceCacheKey(uuid, currency), amount);
                        org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        String pname = of != null && of.getName() != null ? of.getName() : uuid.toString();
                        String pdisplay = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : pname;
                        playerRepo.save(PlayerModel.create(uuid, pname, pdisplay));
                        return;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] MySQL setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
                        return;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            try {
                balanceRepo.save(BalanceModel.create(uuid, currency, amount));
                putCached(balanceCacheKey(uuid, currency), amount);
                org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                String pname = of != null && of.getName() != null ? of.getName() : uuid.toString();
                String pdisplay = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : pname;
                playerRepo.save(PlayerModel.create(uuid, pname, pdisplay));
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
        return withdrawAndGetBalance(uuid, currency, amount).isSuccess();
    }

    @Override
    public void deposit(UUID uuid, String currency, double amount) {
        depositAndGetBalance(uuid, currency, amount);
    }

    @Override
    public EconomyMutationResult depositAndGetBalance(UUID uuid, String currency, double amount) {
        String cacheKey = balanceCacheKey(uuid, currency);
        synchronized (lock) {
            try {
                ensureHotStatements();
                String id = BalanceModel.idFor(uuid, currency);
                psDepositUpsert.setString(1, id);
                psDepositUpsert.setString(2, uuid.toString());
                psDepositUpsert.setString(3, currency);
                psDepositUpsert.setDouble(4, amount);
                psDepositUpsert.executeUpdate();
                double updated;
                psSelectBalanceById.setString(1, id);
                try (ResultSet rs = psSelectBalanceById.executeQuery()) {
                    updated = rs.next() ? rs.getDouble(1) : amount;
                }
                putCached(cacheKey, updated);
                return EconomyMutationResult.success(updated);
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MySQL depositAndGetBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    @Override
    public EconomyMutationResult withdrawAndGetBalance(UUID uuid, String currency, double amount) {
        String cacheKey = balanceCacheKey(uuid, currency);
        synchronized (lock) {
            try {
                ensureHotStatements();
                String id = BalanceModel.idFor(uuid, currency);
                psWithdrawIfEnough.setDouble(1, amount);
                psWithdrawIfEnough.setString(2, id);
                psWithdrawIfEnough.setDouble(3, amount);
                int rows = psWithdrawIfEnough.executeUpdate();
                double current;
                psSelectBalanceById.setString(1, id);
                try (ResultSet rs = psSelectBalanceById.executeQuery()) {
                    current = rs.next() ? rs.getDouble(1) : 0.0;
                }
                if (rows <= 0) return EconomyMutationResult.failure(current, "Insufficient funds");
                putCached(cacheKey, current);
                return EconomyMutationResult.success(current);
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MySQL withdrawAndGetBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    public void shutdown() {
        synchronized (lock) {
            try {
                closeHotStatements();
                if (connection != null && !connection.isClosed()) connection.close();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL shutdown failed: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] Unexpected error on shutdown: " + e.getMessage());
            }
        }
    }
    public Map<UUID, Double> getAllBalances(String currency) {
        synchronized (lock) {
            Map<UUID, Double> map = new ConcurrentHashMap<>();
            try {
                List<BalanceModel> rows = balanceRepo.query(BalanceModel.queryBuilder().whereEquals("currency", currency).build());
                for (BalanceModel bm : rows) {
                    try { map.put(UUID.fromString(bm.getUuid()), bm.getBalance()); } catch (IllegalArgumentException ignored) {}
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL getAllBalances failed (" + currency + "): " + e.getMessage());
            }
            return map;
        }
    }

    @Override
    public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm == null) {
            // No distributed lock available, fall back to default behavior
            double fromBefore = getBalance(fromUuid, currency);
            double toBefore = getBalance(toUuid, currency);

            com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
            if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) {
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);
            }

            com.skyblockexp.ezeconomy.storage.TransferResult result = StorageProvider.super.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);

            com.skyblockexp.ezeconomy.api.events.PostTransactionEvent post = new com.skyblockexp.ezeconomy.api.events.PostTransactionEvent(
                fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER,
                result.isSuccess(), java.math.BigDecimal.valueOf(fromBefore), java.math.BigDecimal.valueOf(result.getFromBalance()),
                java.math.BigDecimal.valueOf(toBefore), java.math.BigDecimal.valueOf(result.getToBalance())
            );
            EventDispatcher.fireSync(plugin, post);

            return result;
        }

        // Acquire distributed locks for both UUIDs in canonical order
        UUID[] ordered = new UUID[]{fromUuid, toUuid};
        if (fromUuid.compareTo(toUuid) > 0) {
            ordered = new UUID[]{toUuid, fromUuid};
        }
        String[] tokens = null;
        try {
            tokens = lm.acquireOrdered(ordered, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (tokens == null) {
            // Couldn't acquire distributed locks; fall back to default transfer
            return StorageProvider.super.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
        }

        try {
            // Re-read balances while holding distributed locks
            double fromBefore;
            double toBefore;
            try {
                fromBefore = balanceRepo.find(BalanceModel.idFor(fromUuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
                toBefore = balanceRepo.find(BalanceModel.idFor(toUuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL transfer balance read failed: " + e.getMessage());
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0.0, 0.0);
            }

            com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
            try {
                EventDispatcher.fireSync(plugin, pre);
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] Failed to fire PreTransactionEvent: " + e.getMessage());
            }
            if (pre.isCancelled()) {
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);
            }

            // Perform withdraw and credit using Jaloquent
            try {
                java.util.Optional<BalanceModel> fromOpt = balanceRepo.find(BalanceModel.idFor(fromUuid, currency));
                double fromBal = fromOpt.map(BalanceModel::getBalance).orElse(0.0);
                if (fromBal < debitAmount) {
                    return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBal, toBefore);
                }
                balanceRepo.transaction(() -> {
                    balanceRepo.save(BalanceModel.create(fromUuid, currency, fromBal - debitAmount));
                    if (creditAmount > 0) {
                        java.util.Optional<BalanceModel> toOpt = balanceRepo.find(BalanceModel.idFor(toUuid, currency));
                        double toBal = toOpt.map(BalanceModel::getBalance).orElse(0.0);
                        balanceRepo.save(BalanceModel.create(toUuid, currency, toBal + creditAmount));
                    }
                });
                double updatedFrom = balanceRepo.find(BalanceModel.idFor(fromUuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
                double updatedTo = balanceRepo.find(BalanceModel.idFor(toUuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
                putCached(balanceCacheKey(fromUuid, currency), updatedFrom);
                putCached(balanceCacheKey(toUuid, currency), updatedTo);
                com.skyblockexp.ezeconomy.storage.TransferResult tr = com.skyblockexp.ezeconomy.storage.TransferResult.success(updatedFrom, updatedTo);

                com.skyblockexp.ezeconomy.api.events.PostTransactionEvent post = new com.skyblockexp.ezeconomy.api.events.PostTransactionEvent(
                    fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER,
                    tr.isSuccess(), java.math.BigDecimal.valueOf(fromBefore), java.math.BigDecimal.valueOf(tr.getFromBalance()),
                    java.math.BigDecimal.valueOf(toBefore), java.math.BigDecimal.valueOf(tr.getToBalance())
                );
                EventDispatcher.fireSync(plugin, post);

                return tr;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL transfer failed: " + e.getMessage());
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);
            }
        } finally {
            lm.releaseOrdered(ordered, tokens);
        }
    }

    // --- Bank support ---
    private void ensureBankTables() {
        if (!schemaInitialized) {
            plugin.getLogger().severe("[EzEconomy] MySQL schema is not initialized. Call init() on server startup.");
            throw new IllegalStateException("MySQL schema not initialized");
        }
    }

    private void ensureHotStatements() throws SQLException {
        if (psDepositUpsert == null || psDepositUpsert.isClosed()) {
            psDepositUpsert = connection.prepareStatement(
                    "INSERT INTO " + table + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)");
        }
        if (psWithdrawIfEnough == null || psWithdrawIfEnough.isClosed()) {
            psWithdrawIfEnough = connection.prepareStatement(
                    "UPDATE " + table + " SET balance = balance - ? WHERE id = ? AND balance >= ?");
        }
        if (psSelectBalanceById == null || psSelectBalanceById.isClosed()) {
            psSelectBalanceById = connection.prepareStatement(
                    "SELECT balance FROM " + table + " WHERE id = ?");
        }
    }

    private void closeHotStatements() {
        try { if (psDepositUpsert != null) psDepositUpsert.close(); } catch (SQLException ignored) {}
        try { if (psWithdrawIfEnough != null) psWithdrawIfEnough.close(); } catch (SQLException ignored) {}
        try { if (psSelectBalanceById != null) psSelectBalanceById.close(); } catch (SQLException ignored) {}
        psDepositUpsert = null;
        psWithdrawIfEnough = null;
        psSelectBalanceById = null;
    }

    public boolean createBank(String name, UUID owner) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        bankRepo.save(BankModel.create(name, "dollar", 0.0));
                        bankMemberRepo.save(BankMemberModel.create(name, owner, true));
                        return true;
                    } catch (StorageException e) {
                        return false;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                bankRepo.save(BankModel.create(name, "dollar", 0.0));
                bankMemberRepo.save(BankMemberModel.create(name, owner, true));
                return true;
            } catch (StorageException e) {
                return false;
            }
        }
    }

    public boolean deleteBank(String name) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        List<BankModel> existing = bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build());
                        bankRepo.deleteWhere("name", name);
                        bankMemberRepo.deleteWhere("bank", name);
                        return !existing.isEmpty();
                    } catch (StorageException e) {
                        return false;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                List<BankModel> existing = bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build());
                bankRepo.deleteWhere("name", name);
                bankMemberRepo.deleteWhere("bank", name);
                return !existing.isEmpty();
            } catch (StorageException e) {
                return false;
            }
        }
    }

    public boolean bankExists(String name) {
        synchronized (lock) {
            ensureBankTables();
            try {
                return !bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build()).isEmpty();
            } catch (StorageException e) {
                return false;
            }
        }
    }

    public double getBankBalance(String name, String currency) {
        Double cached = getCached(bankBalanceCacheKey(name, currency));
        if (cached != null) return cached.doubleValue();
        synchronized (lock) {
            ensureBankTables();
            try {
                double value = bankRepo.find(BankModel.idFor(name, currency)).map(BankModel::getBalance).orElse(0.0);
                putCached(bankBalanceCacheKey(name, currency), value);
                return value;
            } catch (StorageException e) {}
            return 0.0;
        }
    }

    public void setBankBalance(String name, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        bankRepo.save(BankModel.create(name, currency, amount));
                        putCached(bankBalanceCacheKey(name, currency), amount);
                        return;
                    } catch (StorageException e) {}
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                bankRepo.save(BankModel.create(name, currency, amount));
                putCached(bankBalanceCacheKey(name, currency), amount);
            } catch (StorageException e) {}
        }
    }

    @Override
    public boolean tryWithdrawBank(String name, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                        if (!bankOpt.isPresent()) return false;
                        double current = bankOpt.get().getBalance();

                        BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW);
                        if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return false;
                        if (current < amount) return false;

                        bankRepo.save(BankModel.create(name, currency, current - amount));
                        putCached(bankBalanceCacheKey(name, currency), current - amount);
                        EventDispatcher.fireSync(plugin, new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW, true, BigDecimal.valueOf(current), BigDecimal.valueOf(current - amount)));
                        return true;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] MySQL tryWithdrawBank failed: " + e.getMessage());
                        return false;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                if (!bankOpt.isPresent()) return false;
                double current = bankOpt.get().getBalance();

                boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
                if (bankingEnabled) {
                    BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW);
                    if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return false;
                }
                if (current < amount) return false;

                bankRepo.save(BankModel.create(name, currency, current - amount));
                putCached(bankBalanceCacheKey(name, currency), current - amount);
                if (bankingEnabled) {
                    EventDispatcher.fireSync(plugin, new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW, true, BigDecimal.valueOf(current), BigDecimal.valueOf(current - amount)));
                }
                return true;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL tryWithdrawBank failed: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void depositBank(String name, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                        double before = bankOpt.map(BankModel::getBalance).orElse(0.0);

                        BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT);
                        if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return;

                        bankRepo.save(BankModel.create(name, currency, before + amount));
                        putCached(bankBalanceCacheKey(name, currency), before + amount);

                        EventDispatcher.fireSync(plugin, new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before + amount)));
                        return;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] MySQL depositBank failed: " + e.getMessage());
                        return;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                double before = bankOpt.map(BankModel::getBalance).orElse(0.0);

                boolean bankingEnabled = plugin.getConfig().getBoolean("banking.enabled", true);
                if (bankingEnabled) {
                    BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT);
                    if (!EventDispatcher.fireSyncAndAllow(plugin, pre)) return;
                }

                bankRepo.save(BankModel.create(name, currency, before + amount));
                putCached(bankBalanceCacheKey(name, currency), before + amount);

                if (bankingEnabled) {
                    EventDispatcher.fireSync(plugin, new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before + amount)));
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL depositBank failed: " + e.getMessage());
            }
        }
    }

    @Override
    public EconomyMutationResult depositBankAndGetBalance(String name, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        String cacheKey = bankBalanceCacheKey(name, currency);
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                    if (!bankOpt.isPresent()) return EconomyMutationResult.failure(0.0, "Bank does not exist");
                    double updated = bankOpt.get().getBalance() + amount;
                    bankRepo.save(BankModel.create(name, currency, updated));
                    putCached(cacheKey, updated);
                    return EconomyMutationResult.success(updated);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MySQL depositBankAndGetBalance failed: " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            try {
                ensureBankTables();
                java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                if (!bankOpt.isPresent()) return EconomyMutationResult.failure(0.0, "Bank does not exist");
                double updated = bankOpt.get().getBalance() + amount;
                bankRepo.save(BankModel.create(name, currency, updated));
                putCached(cacheKey, updated);
                return EconomyMutationResult.success(updated);
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MySQL depositBankAndGetBalance failed: " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    @Override
    public EconomyMutationResult withdrawBankAndGetBalance(String name, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        String cacheKey = bankBalanceCacheKey(name, currency);
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                    if (!bankOpt.isPresent()) return EconomyMutationResult.failure(0.0, "Bank does not exist");
                    double current = bankOpt.get().getBalance();
                    if (current < amount) return EconomyMutationResult.failure(current, "Insufficient funds");
                    double updated = current - amount;
                    bankRepo.save(BankModel.create(name, currency, updated));
                    putCached(cacheKey, updated);
                    return EconomyMutationResult.success(updated);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MySQL withdrawBankAndGetBalance failed: " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            try {
                ensureBankTables();
                java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                if (!bankOpt.isPresent()) return EconomyMutationResult.failure(0.0, "Bank does not exist");
                double current = bankOpt.get().getBalance();
                if (current < amount) return EconomyMutationResult.failure(current, "Insufficient funds");
                double updated = current - amount;
                bankRepo.save(BankModel.create(name, currency, updated));
                putCached(cacheKey, updated);
                return EconomyMutationResult.success(updated);
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] MySQL withdrawBankAndGetBalance failed: " + e.getMessage());
                return EconomyMutationResult.failure(0.0, "Storage failure");
            }
        }
    }

    public Set<String> getBanks() {
        // bank list is global; use local lock for list operations to avoid distributed overhead
        synchronized (lock) {
            ensureBankTables();
            try {
                return bankRepo.query(BankModel.queryBuilder().build()).stream()
                    .map(BankModel::getName).collect(java.util.stream.Collectors.toSet());
            } catch (StorageException e) {
                return ConcurrentHashMap.newKeySet();
            }
        }
    }

    public boolean isBankOwner(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        return bankMemberRepo.find(BankMemberModel.idFor(name, uuid)).map(BankMemberModel::isOwner).orElse(false);
                    } catch (StorageException e) {}
                    return false;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                return bankMemberRepo.find(BankMemberModel.idFor(name, uuid)).map(BankMemberModel::isOwner).orElse(false);
            } catch (StorageException e) {}
            return false;
        }
    }

    public boolean isBankMember(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        return bankMemberRepo.exists(BankMemberModel.idFor(name, uuid));
                    } catch (StorageException e) {}
                    return false;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                return bankMemberRepo.exists(BankMemberModel.idFor(name, uuid));
            } catch (StorageException e) {}
            return false;
        }
    }

    public boolean addBankMember(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    if (isBankMember(name, uuid)) return false;
                    try {
                        bankMemberRepo.save(BankMemberModel.create(name, uuid, false));
                        return true;
                    } catch (StorageException e) { return false; }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            if (isBankMember(name, uuid)) return false;
            try {
                bankMemberRepo.save(BankMemberModel.create(name, uuid, false));
                return true;
            } catch (StorageException e) { return false; }
        }
    }

    public boolean removeBankMember(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    try {
                        boolean existed = bankMemberRepo.exists(BankMemberModel.idFor(name, uuid));
                        bankMemberRepo.delete(BankMemberModel.idFor(name, uuid));
                        return existed;
                    } catch (StorageException e) { return false; }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            try {
                boolean existed = bankMemberRepo.exists(BankMemberModel.idFor(name, uuid));
                bankMemberRepo.delete(BankMemberModel.idFor(name, uuid));
                return existed;
            } catch (StorageException e) { return false; }
        }
    }

    public Set<UUID> getBankMembers(String name) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    ensureBankTables();
                    Set<UUID> set = ConcurrentHashMap.newKeySet();
                    try {
                        BankModel nameLookup = BankModel.create(name, "", 0.0);
                        nameLookup.members(bankMemberRepo).get()
                            .forEach(m -> { try { set.add(UUID.fromString(m.getMemberUuid())); } catch (IllegalArgumentException ignored) {} });
                    } catch (StorageException e) {}
                    return set;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            ensureBankTables();
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            try {
                BankModel nameLookup = BankModel.create(name, "", 0.0);
                nameLookup.members(bankMemberRepo).get()
                    .forEach(m -> { try { set.add(UUID.fromString(m.getMemberUuid())); } catch (IllegalArgumentException ignored) {} });
            } catch (StorageException e) {}
            return set;
        }
    }

    @Override
    public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction tx) {
        synchronized (lock) {
            try {
                transactionRepo.save(TransactionModel.fromTransaction(tx));
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL logTransaction failed: " + e.getMessage());
            }
        }
    }

    /**
     * Removes balances for UUIDs that do not resolve to a known player.
     * @return Set of removed UUIDs as strings
     */
    public java.util.Set<String> cleanupOrphanedPlayers() {
        java.util.Set<String> removed = new java.util.HashSet<>();
        synchronized (lock) {
            try {
                java.util.List<BalanceModel> all = balanceRepo.query(BalanceModel.queryBuilder().build());
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (BalanceModel bm : all) {
                    String uuidStr = bm.getUuid();
                    if (!seen.add(uuidStr)) continue;
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        if (player == null || player.getName() == null) {
                            removed.add(uuidStr);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
                if (!removed.isEmpty()) {
                    balanceRepo.deleteWhere(
                        BalanceModel.queryBuilder().whereIn("uuid", new java.util.ArrayList<Object>(removed)).build()
                    );
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL cleanupOrphanedPlayers failed: " + e.getMessage());
            }
        }
        return removed;
    }

    /**
     * Returns the set of orphaned UUIDs that would be deleted by cleanup.
     */
    @Override
    public void insertPendingNotification(UUID targetUuid, String message) {
        synchronized (lock) {
            try {
                if (connection == null || connection.isClosed()) return;
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO ezeconomy_pending_notifications (uuid, message, created_at) VALUES (?, ?, NOW())")) {
                    stmt.setString(1, targetUuid.toString());
                    stmt.setString(2, message);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] MySQL insertPendingNotification failed: " + e.getMessage());
            }
        }
    }

    @Override
    public java.util.List<String> pollPendingNotifications(UUID targetUuid) {
        java.util.List<String> messages = new java.util.ArrayList<>();
        synchronized (lock) {
            try {
                if (connection == null || connection.isClosed()) return messages;
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT id, message FROM ezeconomy_pending_notifications WHERE uuid = ? ORDER BY created_at ASC")) {
                    stmt.setString(1, targetUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        java.util.List<Long> ids = new java.util.ArrayList<>();
                        while (rs.next()) {
                            ids.add(rs.getLong("id"));
                            messages.add(rs.getString("message"));
                        }
                        if (!ids.isEmpty()) {
                            String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
                            try (PreparedStatement del = connection.prepareStatement(
                                    "DELETE FROM ezeconomy_pending_notifications WHERE id IN (" + placeholders + ")")) {
                                for (int i = 0; i < ids.size(); i++) {
                                    del.setLong(i + 1, ids.get(i));
                                }
                                del.executeUpdate();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] MySQL pollPendingNotifications failed: " + e.getMessage());
            }
        }
        return messages;
    }

    @Override
    public void cleanupOldNotifications(long olderThanMs) {
        synchronized (lock) {
            try {
                if (connection == null || connection.isClosed()) return;
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM ezeconomy_pending_notifications WHERE created_at < DATE_SUB(NOW(), INTERVAL ? SECOND)")) {
                    stmt.setLong(1, olderThanMs / 1000);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] MySQL cleanupOldNotifications failed: " + e.getMessage());
            }
        }
    }

    @Override
    public UUID resolvePlayerByName(String name) {
        if (name == null) return null;
        synchronized (lock) {
            try {
                if (playerRepo == null) return null;
                java.util.List<PlayerModel> results = playerRepo.query(
                        PlayerModel.queryBuilder()
                                .whereEquals("name", name)
                                .limit(1)
                                .build());
                if (!results.isEmpty()) {
                    try {
                        return UUID.fromString(results.get(0).getId());
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (StorageException e) {
                plugin.getLogger().warning("[EzEconomy] MySQL resolvePlayerByName failed for '" + name + "': " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void persistPlayerInfo(UUID uuid, String name, String displayName) {
        if (uuid == null || name == null) return;
        synchronized (lock) {
            try {
                if (playerRepo == null) return;
                playerRepo.save(PlayerModel.create(uuid, name, displayName != null ? displayName : name));
            } catch (StorageException e) {
                plugin.getLogger().warning("[EzEconomy] MySQL persistPlayerInfo failed for " + uuid + ": " + e.getMessage());
            }
        }
    }

    public java.util.Set<String> previewOrphanedPlayers() {
        java.util.Set<String> orphaned = new java.util.HashSet<>();
        synchronized (lock) {
            try {
                java.util.List<BalanceModel> all = balanceRepo.query(BalanceModel.queryBuilder().build());
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (BalanceModel bm : all) {
                    String uuidStr = bm.getUuid();
                    if (!seen.add(uuidStr)) continue;
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        if (player == null || player.getName() == null) {
                            orphaned.add(uuidStr);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL previewOrphanedPlayers failed: " + e.getMessage());
            }
        }
        return orphaned;
    }
}
