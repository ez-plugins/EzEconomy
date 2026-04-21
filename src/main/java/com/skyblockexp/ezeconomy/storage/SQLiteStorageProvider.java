package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageInitException;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageLoadException;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageSaveException;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.math.BigDecimal;
import com.skyblockexp.ezeconomy.api.events.BankPreTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.BankPostTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.TransactionType;
import org.bukkit.configuration.file.YamlConfiguration;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzSQLiteJdbcStore;
import com.skyblockexp.ezeconomy.storage.jaloquent.EzTableRegistry;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BalanceModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BankMemberModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.BankModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.PlayerModel;
import com.skyblockexp.ezeconomy.storage.jaloquent.model.TransactionModel;

/**
 * SQLite implementation of the StorageProvider interface for EzEconomy.
 * Handles player and bank balances using a local SQLite database.
 * Thread-safe and ready for open-source use.
 *
 * <p>Usage: Instantiate with plugin and config, or call init() if using the default constructor.</p>
 */
public class SQLiteStorageProvider implements StorageProvider {
    // --- Fields ---
    private String fileName;
    private final EzEconomyPlugin plugin;
    private Connection connection;
    private String table;
    private final Object lock = new Object();
    private final YamlConfiguration dbConfig;
    private EzSQLiteJdbcStore jdbcStore;
    private ModelRepository<BalanceModel>     balanceRepo;
    private ModelRepository<PlayerModel>      playerRepo;
    private ModelRepository<BankModel>        bankRepo;
    private ModelRepository<BankMemberModel>  bankMemberRepo;
    private ModelRepository<TransactionModel> transactionRepo;

    // --- Constructors ---
    /**
     * Default constructor for legacy compatibility. Not recommended for production.
     */
    public SQLiteStorageProvider(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.dbConfig = null;
        this.fileName = "economy.db";
        this.table = "balances";
    }

    /**
     * Main constructor. Reads config and initializes tables if needed.
     * Throws RuntimeException if initialization fails.
     * @param plugin EzEconomy plugin instance
     * @param dbConfig YAML configuration for SQLite
     */
    public SQLiteStorageProvider(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
        if (dbConfig == null) throw new IllegalArgumentException("SQLite config is missing!");
        this.fileName = dbConfig.getString("sqlite.file", "ezeconomy.db");
        this.table = dbConfig.getString("sqlite.table", "balances");
        try {
            File file = new File(plugin.getDataFolder(), this.fileName);
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            EzSQLiteJdbcStore tempJdbc = new EzSQLiteJdbcStore(connection);
            java.util.List<Object> noParams = java.util.Collections.emptyList();
            tempJdbc.executeUpdate(QueryBuilder.createTable(table).ifNotExists()
                    .column("id", "TEXT").primaryKey("id")
                    .column("uuid", "TEXT").column("currency", "TEXT").column("balance", "DOUBLE")
                    .build(SqlDialect.SQLITE).getSql(), noParams);
            tempJdbc.executeUpdate(QueryBuilder.createTable("players").ifNotExists()
                    .column("id", "TEXT").primaryKey("id")
                    .column("name", "TEXT").column("displayName", "TEXT")
                    .build(SqlDialect.SQLITE).getSql(), noParams);
            tempJdbc.executeUpdate(QueryBuilder.createTable("banks").ifNotExists()
                    .column("id", "TEXT").primaryKey("id")
                    .column("name", "TEXT").column("currency", "TEXT").column("balance", "DOUBLE")
                    .build(SqlDialect.SQLITE).getSql(), noParams);
            tempJdbc.executeUpdate(QueryBuilder.createTable("bank_members").ifNotExists()
                    .column("id", "TEXT").primaryKey("id")
                    .column("bank", "TEXT").column("uuid", "TEXT").column("owner", "INTEGER")
                    .build(SqlDialect.SQLITE).getSql(), noParams);
            tempJdbc.executeUpdate(QueryBuilder.createTable("transactions").ifNotExists()
                    .column("id", "TEXT").primaryKey("id")
                    .column("uuid", "TEXT").column("currency", "TEXT").column("amount", "DOUBLE").column("timestamp", "INTEGER")
                    .build(SqlDialect.SQLITE).getSql(), noParams);
            initRepositories();
        } catch (Exception e) {
            plugin.getLogger().severe("SQLite connection failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize SQLiteStorageProvider", e);
        }
    }

    // --- Public API: StorageProvider interface ---
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
                plugin.getLogger().severe("[EzEconomy] SQLite getTransactions failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
        return transactions;
    }

    /**
     * Initializes the SQLite connection and tables. Call before use if not using the config constructor.
     * @throws StorageInitException if the JDBC driver is missing or connection fails
     */
    public void init() throws StorageInitException {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), fileName).getAbsolutePath());
            createTableIfNotExists();
        } catch (ClassNotFoundException e) {
            throw new StorageInitException("SQLite JDBC driver not found.", e);
        } catch (SQLException e) {
            throw new StorageInitException("Failed to connect to the database.", e);
        }
    }

    /**
     * Creates the default economy table if it does not exist.
     */
    private void createTableIfNotExists() throws StorageInitException {
        String sql = "CREATE TABLE IF NOT EXISTS economy (" +
                "uuid TEXT PRIMARY KEY NOT NULL," +
                "balance REAL DEFAULT 0," +
                "last_updated INTEGER" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new StorageInitException("Failed to create table in the database.", e);
        }
    }

    /**
     * Loads all player balances from the economy table. No-op unless you add caching.
     * @throws StorageLoadException if loading fails
     */
    public void load() throws StorageLoadException {
        // No in-memory cache, so nothing to load. If you add caching, load from DB here.
    }

    /**
     * Saves all in-memory data to the database. No-op unless you add caching.
     * @throws StorageSaveException if saving fails
     */
    public void save() throws StorageSaveException {
        // No in-memory cache, so nothing to save. If you add caching, flush to DB here.
    }
    // Optionally, override equals/hashCode/toString if needed for provider management
    @Override
    public String toString() {
        return "SQLiteStorageProvider{" +
                "fileName='" + fileName + '\'' +
                ", table='" + table + '\'' +
                '}' ;
    }

    /**
     * Closes the SQLite connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to close the database connection.", e);
            }
        }
    }

    /**
     * Initialises Jaloquent repositories for balances, players, and transactions.
     * Called automatically at the end of the config constructor.
     */
    public void initRepositories() {
        EzTableRegistry.registerAll(table, "players", "banks", "bank_members", "transactions");
        jdbcStore       = new EzSQLiteJdbcStore(connection);
        balanceRepo     = new ModelRepository<>(jdbcStore, BalanceModel.PREFIX,    BalanceModel::new,    SqlDialect.SQLITE);
        playerRepo      = new ModelRepository<>(jdbcStore, PlayerModel.PREFIX,     PlayerModel::new,     SqlDialect.SQLITE);
        bankRepo        = new ModelRepository<>(jdbcStore, BankModel.PREFIX,       BankModel::new,       SqlDialect.SQLITE);
        bankMemberRepo  = new ModelRepository<>(jdbcStore, BankMemberModel.PREFIX, BankMemberModel::new, SqlDialect.SQLITE);
        transactionRepo = new ModelRepository<>(jdbcStore, TransactionModel.PREFIX,TransactionModel::new,SqlDialect.SQLITE);
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        return balanceRepo.find(BalanceModel.idFor(uuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite getBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
                    }
                    return 0.0;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(uuid, token);
            }
        }
        synchronized (lock) {
            try {
                return balanceRepo.find(BalanceModel.idFor(uuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
            return 0.0;
        }
    }

    @Override
    public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        java.util.Optional<PlayerModel> opt = playerRepo.find(uuid.toString());
                        if (opt.isPresent()) {
                            PlayerModel pm = opt.get();
                            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, pm.getName(), pm.getDisplayName());
                        }
                    } catch (StorageException ignored) {}
                    org.bukkit.OfflinePlayer of = plugin.getServer().getOfflinePlayer(uuid);
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
                java.util.Optional<PlayerModel> opt = playerRepo.find(uuid.toString());
                if (opt.isPresent()) {
                    PlayerModel pm = opt.get();
                    return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, pm.getName(), pm.getDisplayName());
                }
            } catch (StorageException ignored) {}
            org.bukkit.OfflinePlayer of = plugin.getServer().getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
        }
    }

    @Override
    public boolean playerExists(UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        return !balanceRepo.query(BalanceModel.queryBuilder().whereEquals("uuid", uuid.toString()).limit(1).build()).isEmpty();
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite playerExists failed for " + uuid + ": " + e.getMessage());
                        return false;
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
                return !balanceRepo.query(BalanceModel.queryBuilder().whereEquals("uuid", uuid.toString()).limit(1).build()).isEmpty();
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite playerExists failed for " + uuid + ": " + e.getMessage());
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
                        org.bukkit.OfflinePlayer of = plugin.getServer().getOfflinePlayer(uuid);
                        String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                        String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                        try { playerRepo.save(PlayerModel.create(uuid, name, display)); } catch (StorageException ignored) {}
                        return;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
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
                org.bukkit.OfflinePlayer of = plugin.getServer().getOfflinePlayer(uuid);
                String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                try { playerRepo.save(PlayerModel.create(uuid, name, display)); } catch (StorageException ignored) {}
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        java.util.Optional<BalanceModel> opt = balanceRepo.find(BalanceModel.idFor(uuid, currency));
                        double current = opt.map(BalanceModel::getBalance).orElse(0.0);
                        if (current < amount) return false;
                        balanceRepo.save(BalanceModel.create(uuid, currency, current - amount));
                        return true;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite tryWithdraw failed for " + uuid + " (" + currency + "): " + e.getMessage());
                        return false;
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
                java.util.Optional<BalanceModel> opt = balanceRepo.find(BalanceModel.idFor(uuid, currency));
                double current = opt.map(BalanceModel::getBalance).orElse(0.0);
                if (current < amount) return false;
                balanceRepo.save(BalanceModel.create(uuid, currency, current - amount));
                return true;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite tryWithdraw failed for " + uuid + " (" + currency + "): " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void deposit(UUID uuid, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(uuid, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        java.util.Optional<BalanceModel> opt = balanceRepo.find(BalanceModel.idFor(uuid, currency));
                        double current = opt.map(BalanceModel::getBalance).orElse(0.0);
                        balanceRepo.save(BalanceModel.create(uuid, currency, current + amount));
                        org.bukkit.OfflinePlayer of = plugin.getServer().getOfflinePlayer(uuid);
                        String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                        String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                        try { playerRepo.save(PlayerModel.create(uuid, name, display)); } catch (StorageException ignored) {}
                        return;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite deposit failed for " + uuid + " (" + currency + "): " + e.getMessage());
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
                java.util.Optional<BalanceModel> opt = balanceRepo.find(BalanceModel.idFor(uuid, currency));
                double current = opt.map(BalanceModel::getBalance).orElse(0.0);
                balanceRepo.save(BalanceModel.create(uuid, currency, current + amount));
                org.bukkit.OfflinePlayer of = plugin.getServer().getOfflinePlayer(uuid);
                String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
                String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
                try { playerRepo.save(PlayerModel.create(uuid, name, display)); } catch (StorageException ignored) {}
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite deposit failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances(String currency) {
        Map<UUID, Double> map = new HashMap<>();
        synchronized (lock) {
            try {
                java.util.List<BalanceModel> rows = balanceRepo.query(
                    BalanceModel.queryBuilder().whereEquals("currency", currency).build());
                for (BalanceModel bm : rows) {
                    try {
                        map.put(UUID.fromString(bm.getUuid()), bm.getBalance());
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getAllBalances failed: " + e.getMessage());
            }
        }
        return map;
    }

    @Override
    public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        if (lm == null) {
            double fromBefore = getBalance(fromUuid, currency);
            double toBefore = getBalance(toUuid, currency);
            com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
            try {
                plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                    plugin.getServer().getPluginManager().callEvent(pre);
                    return null;
                }).get();
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] Failed to fire PreTransactionEvent: " + e.getMessage());
            }
            if (pre.isCancelled()) {
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);
            }
            com.skyblockexp.ezeconomy.storage.TransferResult result = StorageProvider.super.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
            com.skyblockexp.ezeconomy.api.events.PostTransactionEvent post = new com.skyblockexp.ezeconomy.api.events.PostTransactionEvent(
                fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER,
                result.isSuccess(), java.math.BigDecimal.valueOf(fromBefore), java.math.BigDecimal.valueOf(result.getFromBalance()),
                java.math.BigDecimal.valueOf(toBefore), java.math.BigDecimal.valueOf(result.getToBalance())
            );
            try {
                plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                    plugin.getServer().getPluginManager().callEvent(post);
                    return null;
                }).get();
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] Failed to fire PostTransactionEvent: " + e.getMessage());
            }
            return result;
        }

        UUID[] ordered = new UUID[]{fromUuid, toUuid};
        if (fromUuid.compareTo(toUuid) > 0) ordered = new UUID[]{toUuid, fromUuid};
        String[] tokens = null;
        try {
            tokens = lm.acquireOrdered(ordered, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (tokens == null) {
            return StorageProvider.super.transfer(fromUuid, toUuid, currency, debitAmount, creditAmount);
        }

        try {
            double fromBefore;
            double toBefore;
            try {
                fromBefore = balanceRepo.find(BalanceModel.idFor(fromUuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
                toBefore = balanceRepo.find(BalanceModel.idFor(toUuid, currency)).map(BalanceModel::getBalance).orElse(0.0);
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite transfer balance read failed: " + e.getMessage());
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(0.0, 0.0);
            }

            com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
            try {
                plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                    plugin.getServer().getPluginManager().callEvent(pre);
                    return null;
                }).get();
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] Failed to fire PreTransactionEvent: " + e.getMessage());
            }
            if (pre.isCancelled()) return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);

            try {
                java.util.Optional<BalanceModel> fromOpt = balanceRepo.find(BalanceModel.idFor(fromUuid, currency));
                double fromBal = fromOpt.map(BalanceModel::getBalance).orElse(0.0);
                if (fromBal < debitAmount) {
                    double refreshedFrom = getBalance(fromUuid, currency);
                    double refreshedTo = getBalance(toUuid, currency);
                    return com.skyblockexp.ezeconomy.storage.TransferResult.failure(refreshedFrom, refreshedTo);
                }
                balanceRepo.transaction(() -> {
                    balanceRepo.save(BalanceModel.create(fromUuid, currency, fromBal - debitAmount));
                    if (creditAmount > 0) {
                        java.util.Optional<BalanceModel> toOpt = balanceRepo.find(BalanceModel.idFor(toUuid, currency));
                        double toBal = toOpt.map(BalanceModel::getBalance).orElse(0.0);
                        balanceRepo.save(BalanceModel.create(toUuid, currency, toBal + creditAmount));
                    }
                });
                double updatedFrom = getBalance(fromUuid, currency);
                double updatedTo = getBalance(toUuid, currency);
                com.skyblockexp.ezeconomy.storage.TransferResult tr = com.skyblockexp.ezeconomy.storage.TransferResult.success(updatedFrom, updatedTo);

                com.skyblockexp.ezeconomy.api.events.PostTransactionEvent post = new com.skyblockexp.ezeconomy.api.events.PostTransactionEvent(
                    fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER,
                    tr.isSuccess(), java.math.BigDecimal.valueOf(fromBefore), java.math.BigDecimal.valueOf(tr.getFromBalance()),
                    java.math.BigDecimal.valueOf(toBefore), java.math.BigDecimal.valueOf(tr.getToBalance())
                );
                try {
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        plugin.getServer().getPluginManager().callEvent(post);
                        return null;
                    }).get();
                } catch (Exception e) {
                    plugin.getLogger().warning("[EzEconomy] Failed to fire PostTransactionEvent: " + e.getMessage());
                }
                return tr;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite transfer failed: " + e.getMessage());
                return com.skyblockexp.ezeconomy.storage.TransferResult.failure(fromBefore, toBefore);
            }
        } finally {
            lm.releaseOrdered(ordered, tokens);
        }
    }

    @Override
    public void shutdown() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
    }

    // --- Bank support ---

    @Override
    public boolean createBank(String name, UUID owner) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        if (bankExists(name)) return false;
                        bankRepo.save(BankModel.create(name, "dollar", 0.0));
                        bankMemberRepo.save(BankMemberModel.create(name, owner, true));
                        return true;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite createBank failed: " + e.getMessage());
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
            try {
                if (bankExists(name)) return false;
                bankRepo.save(BankModel.create(name, "dollar", 0.0));
                bankMemberRepo.save(BankMemberModel.create(name, owner, true));
                return true;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite createBank failed: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public boolean deleteBank(String name) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        java.util.List<BankModel> existing = bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build());
                        bankRepo.deleteWhere("name", name);
                        bankMemberRepo.deleteWhere("bank", name);
                        return !existing.isEmpty();
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite deleteBank failed: " + e.getMessage());
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
            try {
                java.util.List<BankModel> existing = bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build());
                bankRepo.deleteWhere("name", name);
                bankMemberRepo.deleteWhere("bank", name);
                return !existing.isEmpty();
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite deleteBank failed: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public boolean bankExists(String name) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        return !bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build()).isEmpty();
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite bankExists failed: " + e.getMessage());
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
            try {
                return !bankRepo.query(BankModel.queryBuilder().whereEquals("name", name).build()).isEmpty();
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite bankExists failed: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public double getBankBalance(String name, String currency) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        return bankRepo.find(BankModel.idFor(name, currency)).map(BankModel::getBalance).orElse(0.0);
                    } catch (StorageException e) {}
                    return 0.0;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (token != null) lm.release(bankId, token);
            }
        }
        synchronized (lock) {
            try {
                return bankRepo.find(BankModel.idFor(name, currency)).map(BankModel::getBalance).orElse(0.0);
            } catch (StorageException e) {}
            return 0.0;
        }
    }

    @Override
    public void setBankBalance(String name, String currency, double amount) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
                    try {
                        bankRepo.save(BankModel.create(name, currency, amount));
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
            try {
                bankRepo.save(BankModel.create(name, currency, amount));
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
                    try {
                        java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                        if (!bankOpt.isPresent()) return false;
                        double current = bankOpt.get().getBalance();
                        BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW);
                        if (plugin.getServer().isPrimaryThread()) {
                            plugin.getServer().getPluginManager().callEvent(pre);
                        } else {
                            try {
                                plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                                    plugin.getServer().getPluginManager().callEvent(pre);
                                    return null;
                                }).get();
                            } catch (Exception e) {
                                plugin.getLogger().warning("[EzEconomy] Failed to fire BankPreTransactionEvent: " + e.getMessage());
                            }
                        }
                        if (pre.isCancelled()) return false;
                        if (current < amount) return false;
                        bankRepo.save(BankModel.create(name, currency, current - amount));
                        BankPostTransactionEvent post = new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW, true, BigDecimal.valueOf(current), BigDecimal.valueOf(current - amount));
                        try {
                            plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                                plugin.getServer().getPluginManager().callEvent(post);
                                return null;
                            }).get();
                        } catch (Exception e) {
                            plugin.getLogger().warning("[EzEconomy] Failed to fire BankPostTransactionEvent: " + e.getMessage());
                        }
                        return true;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite tryWithdrawBank failed: " + e.getMessage());
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
            try {
                java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                if (!bankOpt.isPresent()) return false;
                double current = bankOpt.get().getBalance();
                BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW);
                if (plugin.getServer().isPrimaryThread()) {
                    plugin.getServer().getPluginManager().callEvent(pre);
                } else {
                    try {
                        plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                            plugin.getServer().getPluginManager().callEvent(pre);
                            return null;
                        }).get();
                    } catch (Exception e) {
                        plugin.getLogger().warning("[EzEconomy] Failed to fire BankPreTransactionEvent: " + e.getMessage());
                    }
                }
                if (pre.isCancelled()) return false;
                if (current < amount) return false;
                bankRepo.save(BankModel.create(name, currency, current - amount));
                BankPostTransactionEvent post = new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW, true, BigDecimal.valueOf(current), BigDecimal.valueOf(current - amount));
                try {
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        plugin.getServer().getPluginManager().callEvent(post);
                        return null;
                    }).get();
                } catch (Exception e) {
                    plugin.getLogger().warning("[EzEconomy] Failed to fire BankPostTransactionEvent: " + e.getMessage());
                }
                return true;
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite tryWithdrawBank failed: " + e.getMessage());
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
                    try {
                        java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                        double before = bankOpt.map(BankModel::getBalance).orElse(0.0);
                        BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT);
                        try {
                            plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                                plugin.getServer().getPluginManager().callEvent(pre);
                                return null;
                            }).get();
                        } catch (Exception e) {
                            plugin.getLogger().warning("[EzEconomy] Failed to fire BankPreTransactionEvent: " + e.getMessage());
                        }
                        if (pre.isCancelled()) return;
                        bankRepo.save(BankModel.create(name, currency, before + amount));
                        BankPostTransactionEvent post = new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before + amount));
                        if (plugin.getServer().isPrimaryThread()) {
                            plugin.getServer().getPluginManager().callEvent(post);
                        } else {
                            try {
                                plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                                    plugin.getServer().getPluginManager().callEvent(post);
                                    return null;
                                }).get();
                            } catch (Exception e) {
                                plugin.getLogger().warning("[EzEconomy] Failed to fire BankPostTransactionEvent: " + e.getMessage());
                            }
                        }
                        return;
                    } catch (StorageException e) {
                        plugin.getLogger().severe("[EzEconomy] SQLite depositBank failed: " + e.getMessage());
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
            try {
                java.util.Optional<BankModel> bankOpt = bankRepo.find(BankModel.idFor(name, currency));
                double before = bankOpt.map(BankModel::getBalance).orElse(0.0);
                BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT);
                try {
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        plugin.getServer().getPluginManager().callEvent(pre);
                        return null;
                    }).get();
                } catch (Exception e) {
                    plugin.getLogger().warning("[EzEconomy] Failed to fire BankPreTransactionEvent: " + e.getMessage());
                }
                if (pre.isCancelled()) return;
                bankRepo.save(BankModel.create(name, currency, before + amount));
                BankPostTransactionEvent post = new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before + amount));
                if (plugin.getServer().isPrimaryThread()) {
                    plugin.getServer().getPluginManager().callEvent(post);
                } else {
                    try {
                        plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                            plugin.getServer().getPluginManager().callEvent(post);
                            return null;
                        }).get();
                    } catch (Exception e) {
                        plugin.getLogger().warning("[EzEconomy] Failed to fire BankPostTransactionEvent: " + e.getMessage());
                    }
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite depositBank failed: " + e.getMessage());
            }
        }
    }

    @Override
    public Set<String> getBanks() {
        synchronized (lock) {
            try {
                return bankRepo.query(BankModel.queryBuilder().build()).stream()
                    .map(BankModel::getName).collect(java.util.stream.Collectors.toSet());
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBanks failed: " + e.getMessage());
                return new HashSet<>();
            }
        }
    }

    @Override
    public boolean isBankOwner(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
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
            try {
                return bankMemberRepo.find(BankMemberModel.idFor(name, uuid)).map(BankMemberModel::isOwner).orElse(false);
            } catch (StorageException e) {}
            return false;
        }
    }

    @Override
    public boolean isBankMember(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
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
            try {
                return bankMemberRepo.exists(BankMemberModel.idFor(name, uuid));
            } catch (StorageException e) {}
            return false;
        }
    }

    @Override
    public boolean addBankMember(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
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
            if (isBankMember(name, uuid)) return false;
            try {
                bankMemberRepo.save(BankMemberModel.create(name, uuid, false));
                return true;
            } catch (StorageException e) { return false; }
        }
    }

    @Override
    public boolean removeBankMember(String name, UUID uuid) {
        com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
        UUID bankId = UUID.nameUUIDFromBytes(name.getBytes());
        if (lm != null) {
            String token = null;
            try {
                token = lm.acquire(bankId, plugin.getConfig().getLong("redis.ttl-ms", 5000), plugin.getConfig().getLong("redis.retry-ms", 50), plugin.getConfig().getInt("redis.max-attempts", 100));
                if (token != null) {
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
            try {
                boolean existed = bankMemberRepo.exists(BankMemberModel.idFor(name, uuid));
                bankMemberRepo.delete(BankMemberModel.idFor(name, uuid));
                return existed;
            } catch (StorageException e) { return false; }
        }
    }

    @Override
    public Set<UUID> getBankMembers(String name) {
        Set<UUID> set = new HashSet<>();
        synchronized (lock) {
            try {
                // Navigate via the BankModel.members() relationship: creates a lookup
                // instance with the bank name set to drive the HasMany query.
                BankModel nameLookup = BankModel.create(name, "", 0.0);
                nameLookup.members(bankMemberRepo).get()
                    .forEach(m -> { try { set.add(UUID.fromString(m.getMemberUuid())); } catch (IllegalArgumentException ignored) {} });
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBankMembers failed: " + e.getMessage());
            }
        }
        return set;
    }

    @Override
    public void logTransaction(com.skyblockexp.ezeconomy.api.storage.models.Transaction tx) {
        synchronized (lock) {
            try {
                transactionRepo.save(TransactionModel.fromTransaction(tx));
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite logTransaction failed: " + e.getMessage());
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
                        UUID uuid = UUID.fromString(uuidStr);
                        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        if (player == null || player.getName() == null) {
                            orphaned.add(uuidStr);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (StorageException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite previewOrphanedPlayers failed: " + e.getMessage());
            }
        }
        return orphaned;
    }
}
