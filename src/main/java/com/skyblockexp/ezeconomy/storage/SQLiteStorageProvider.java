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
    private String banksTable;
    private final Object lock = new Object();
    private final YamlConfiguration dbConfig;

    // --- Constructors ---
    /**
     * Default constructor for legacy compatibility. Not recommended for production.
     */
    public SQLiteStorageProvider(EzEconomyPlugin plugin) {
        this.plugin = plugin;
        this.dbConfig = null;
        this.fileName = "economy.db";
        this.table = "balances";
        this.banksTable = "banks";
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
        this.banksTable = dbConfig.getString("sqlite.banksTable", "banks");
        try {
            File file = new File(plugin.getDataFolder(), this.fileName);
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS '" + table + "' (uuid TEXT, currency TEXT, balance DOUBLE, PRIMARY KEY (uuid, currency))");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS '" + banksTable + "' (name TEXT PRIMARY KEY, owner TEXT, members TEXT, balances TEXT)");
        } catch (SQLException e) {
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
                // Assumes a table: transactions(uuid TEXT, currency TEXT, amount DOUBLE, timestamp INTEGER)
                String sql = "SELECT amount, timestamp FROM transactions WHERE uuid=? AND currency=? ORDER BY timestamp DESC";
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    double amount = rs.getDouble("amount");
                    long timestamp = rs.getLong("timestamp");
                    Transaction t = new Transaction(uuid, currency, amount, timestamp);
                    transactions.add(t);
                }
            } catch (SQLException e) {
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
                ", banksTable='" + banksTable + '\'' +
                '}';
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

    @Override
    public double getBalance(UUID uuid, String currency) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balance FROM '" + table + "' WHERE uuid=? AND currency=?");
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getDouble(1);
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
            return 0.0;
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("REPLACE INTO '" + table + "' (uuid, currency, balance) VALUES (?, ?, ?)");
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "UPDATE '" + table + "' SET balance = balance - ? WHERE uuid=? AND currency=? AND balance >= ?"
                );
                ps.setDouble(1, amount);
                ps.setString(2, uuid.toString());
                ps.setString(3, currency);
                ps.setDouble(4, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite tryWithdraw failed for " + uuid + " (" + currency + "): " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void deposit(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO '" + table + "' (uuid, currency, balance) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid, currency) DO UPDATE SET balance = balance + excluded.balance"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite deposit failed for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances(String currency) {
        Map<UUID, Double> map = new HashMap<>();
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT uuid, balance FROM '" + table + "' WHERE currency=?");
                ps.setString(1, currency);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    map.put(UUID.fromString(rs.getString(1)), rs.getDouble(2));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getAllBalances failed: " + e.getMessage());
            }
        }
        return map;
    }

    @Override
    public com.skyblockexp.ezeconomy.storage.TransferResult transfer(UUID fromUuid, UUID toUuid, String currency, double debitAmount, double creditAmount) {
        double fromBefore = getBalance(fromUuid, currency);
        double toBefore = getBalance(toUuid, currency);

        com.skyblockexp.ezeconomy.api.events.PreTransactionEvent pre = new com.skyblockexp.ezeconomy.api.events.PreTransactionEvent(fromUuid, toUuid, java.math.BigDecimal.valueOf(debitAmount), com.skyblockexp.ezeconomy.api.events.TransactionType.TRANSFER);
        try {
            // Fire synchronously on main thread and wait for result
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

        // Perform the actual transfer using the default implementation
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

    @Override
    public void shutdown() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
    }

    @Override
    public boolean createBank(String name, UUID owner) {
        synchronized (lock) {
            if (bankExists(name)) return false;
            try {
                String members = owner.toString();
                String balances = "{}";
                PreparedStatement ps = connection.prepareStatement("INSERT INTO '" + banksTable + "' (name, owner, members, balances) VALUES (?, ?, ?, ?)");
                ps.setString(1, name);
                ps.setString(2, owner.toString());
                ps.setString(3, members);
                ps.setString(4, balances);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite createBank failed: " + e.getMessage());
                return false;
            }
        }
    }
    @Override
    public boolean deleteBank(String name) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("DELETE FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite deleteBank failed: " + e.getMessage());
                return false;
            }
        }
    }
    @Override
    public boolean bankExists(String name) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite bankExists failed: " + e.getMessage());
                return false;
            }
        }
    }
    @Override
    public double getBankBalance(String name, String currency) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balances FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String balancesJson = rs.getString(1);
                    Map<String, Double> balances = parseBalances(balancesJson);
                    return balances.getOrDefault(currency, 0.0);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBankBalance failed: " + e.getMessage());
            }
            return 0.0;
        }
    }
    @Override
    public void setBankBalance(String name, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balances FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                Map<String, Double> balances = new HashMap<>();
                if (rs.next()) {
                    balances = parseBalances(rs.getString(1));
                }
                balances.put(currency, amount);
                String newJson = toJson(balances);
                PreparedStatement ps2 = connection.prepareStatement("UPDATE '" + banksTable + "' SET balances=? WHERE name=?");
                ps2.setString(1, newJson);
                ps2.setString(2, name);
                ps2.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite setBankBalance failed: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean tryWithdrawBank(String name, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balances FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    return false;
                }
                Map<String, Double> balances = parseBalances(rs.getString(1));
                double current = balances.getOrDefault(currency, 0.0);
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
                if (pre.isCancelled()) {
                    return false;
                }
                if (current < amount) {
                    return false;
                }
                balances.put(currency, current - amount);
                String newJson = toJson(balances);
                PreparedStatement ps2 = connection.prepareStatement("UPDATE '" + banksTable + "' SET balances=? WHERE name=?");
                ps2.setString(1, newJson);
                ps2.setString(2, name);
                ps2.executeUpdate();
                // Fire post event
                BankPostTransactionEvent post = new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_WITHDRAW, true, BigDecimal.valueOf(current), BigDecimal.valueOf(current - amount));
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
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite tryWithdrawBank failed: " + e.getMessage());
                return false;
            }
        }
    }

    @Override
    public void depositBank(String name, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balances FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    return;
                }
                Map<String, Double> balances = parseBalances(rs.getString(1));
                double before = balances.getOrDefault(currency, 0.0);

                BankPreTransactionEvent pre = new BankPreTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT);
                try {
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        plugin.getServer().getPluginManager().callEvent(pre);
                        return null;
                    }).get();
                } catch (Exception e) {
                    plugin.getLogger().warning("[EzEconomy] Failed to fire BankPreTransactionEvent: " + e.getMessage());
                }
                if (pre.isCancelled()) {
                    return;
                }

                balances.put(currency, before + amount);
                String newJson = toJson(balances);
                PreparedStatement ps2 = connection.prepareStatement("UPDATE '" + banksTable + "' SET balances=? WHERE name=?");
                ps2.setString(1, newJson);
                ps2.setString(2, name);
                ps2.executeUpdate();

                // Fire post event
                BankPostTransactionEvent post = new BankPostTransactionEvent(name, null, BigDecimal.valueOf(amount), TransactionType.BANK_DEPOSIT, true, BigDecimal.valueOf(before), BigDecimal.valueOf(before + amount));
                try {
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        plugin.getServer().getPluginManager().callEvent(post);
                        return null;
                    }).get();
                } catch (Exception e) {
                    plugin.getLogger().warning("[EzEconomy] Failed to fire BankPostTransactionEvent: " + e.getMessage());
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite depositBank failed: " + e.getMessage());
            }
        }
    }
    @Override
    public Set<String> getBanks() {
        Set<String> set = new HashSet<>();
        synchronized (lock) {
            try {
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT name FROM '" + banksTable + "'");
                while (rs.next()) set.add(rs.getString(1));
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBanks failed: " + e.getMessage());
            }
        }
        return set;
    }
    @Override
    public boolean isBankOwner(String name, UUID uuid) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT owner FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return uuid.toString().equals(rs.getString(1));
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite isBankOwner failed: " + e.getMessage());
            }
            return false;
        }
    }
    @Override
    public boolean isBankMember(String name, UUID uuid) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT members FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Set<String> members = parseMembers(rs.getString(1));
                    return members.contains(uuid.toString());
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite isBankMember failed: " + e.getMessage());
            }
            return false;
        }
    }
    @Override
    public boolean addBankMember(String name, UUID uuid) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT members FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                Set<String> members = new HashSet<>();
                if (rs.next()) {
                    members = parseMembers(rs.getString(1));
                }
                if (!members.add(uuid.toString())) return false;
                PreparedStatement ps2 = connection.prepareStatement("UPDATE '" + banksTable + "' SET members=? WHERE name=?");
                ps2.setString(1, toMemberString(members));
                ps2.setString(2, name);
                ps2.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite addBankMember failed: " + e.getMessage());
                return false;
            }
        }
    }
    @Override
    public boolean removeBankMember(String name, UUID uuid) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT members FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                Set<String> members = new HashSet<>();
                if (rs.next()) {
                    members = parseMembers(rs.getString(1));
                }
                if (!members.remove(uuid.toString())) return false;
                PreparedStatement ps2 = connection.prepareStatement("UPDATE '" + banksTable + "' SET members=? WHERE name=?");
                ps2.setString(1, toMemberString(members));
                ps2.setString(2, name);
                ps2.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite removeBankMember failed: " + e.getMessage());
                return false;
            }
        }
    }
    @Override
    public Set<UUID> getBankMembers(String name) {
        Set<UUID> set = new HashSet<>();
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT members FROM '" + banksTable + "' WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Set<String> members = parseMembers(rs.getString(1));
                    for (String s : members) {
                        try { set.add(UUID.fromString(s)); } catch (Exception ignored) {}
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite getBankMembers failed: " + e.getMessage());
            }
        }
        return set;
    }
    
    @Override
    public void logTransaction(Transaction tx) {
        synchronized (lock) {
            try {
                String sql = "INSERT INTO transactions (uuid, currency, amount, timestamp) VALUES (?, ?, ?, ?)";
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, tx.getUuid().toString());
                ps.setString(2, tx.getCurrency());
                ps.setDouble(3, tx.getAmount());
                ps.setLong(4, tx.getTimestamp());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite logTransaction failed: " + e.getMessage());
            }
        }
    }

    /**
     * Removes balances for UUIDs that do not resolve to a known player.
     * @return Set of removed UUIDs as strings
     */
    public Set<String> cleanupOrphanedPlayers() {
        Set<String> removed = new HashSet<>();
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM '" + table + "'");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String uuidStr = rs.getString(1);
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        if (player == null || player.getName() == null) {
                            PreparedStatement del = connection.prepareStatement("DELETE FROM '" + table + "' WHERE uuid=?");
                            del.setString(1, uuidStr);
                            del.executeUpdate();
                            removed.add(uuidStr);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite cleanupOrphanedPlayers failed: " + e.getMessage());
            }
        }
        return removed;
    }

    /**
     * Returns the set of orphaned UUIDs that would be deleted by cleanup.
     */
    public java.util.Set<String> previewOrphanedPlayers() {
        java.util.Set<String> orphaned = new java.util.HashSet<>();
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM '" + table + "'");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String uuidStr = rs.getString(1);
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                        if (player == null || player.getName() == null) {
                            orphaned.add(uuidStr);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] SQLite previewOrphanedPlayers failed: " + e.getMessage());
            }
        }
        return orphaned;
    }

    // --- Helper methods for bank serialization ---
    private Map<String, Double> parseBalances(String json) {
        Map<String, Double> map = new HashMap<>();
        if (json == null || json.isEmpty() || json.equals("{}")) return map;
        // Simple format: {"USD":100.0,"EUR":50.0}
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] entries = json.split(",");
            for (String entry : entries) {
                String[] kv = entry.split(":");
                if (kv.length == 2) {
                    String k = kv[0].replaceAll("[\"{}]", "").trim();
                    try { map.put(k, Double.parseDouble(kv[1])); } catch (Exception ignored) {}
                }
            }
        }
        return map;
    }

    private String toJson(Map<String, Double> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Set<String> parseMembers(String s) {
        Set<String> set = new HashSet<>();
        if (s == null || s.isEmpty()) return set;
        // Members are stored as comma-separated UUIDs
        for (String part : s.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
    }

    private String toMemberString(Set<String> set) {
        return String.join(",", set);
    }
}
