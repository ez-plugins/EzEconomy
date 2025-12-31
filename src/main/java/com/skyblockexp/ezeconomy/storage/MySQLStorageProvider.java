package com.skyblockexp.ezeconomy.storage;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.exceptions.StorageException;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import org.bukkit.configuration.file.YamlConfiguration;

import java.sql.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MySQLStorageProvider implements StorageProvider {
    private final EzEconomyPlugin plugin;
    private Connection connection;
    private String table;
    private final Object lock = new Object();
    private final YamlConfiguration dbConfig;

    public MySQLStorageProvider(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
        if (dbConfig == null) throw new IllegalArgumentException("MySQL config is missing!");
        String host = dbConfig.getString("mysql.host");
        int port = dbConfig.getInt("mysql.port");
        String database = dbConfig.getString("mysql.database");
        String username = dbConfig.getString("mysql.username");
        String password = dbConfig.getString("mysql.password");
        this.table = dbConfig.getString("mysql.table", "balances");
        try {
            connection = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database,
                username, password);
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + table + "` (uuid VARCHAR(36), currency VARCHAR(32), balance DOUBLE, PRIMARY KEY (uuid, currency))");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS banks (name VARCHAR(64), currency VARCHAR(32), balance DOUBLE, PRIMARY KEY (name, currency))");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bank_members (bank VARCHAR(64), uuid VARCHAR(36), owner BOOLEAN, PRIMARY KEY (bank, uuid))");
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL connection failed: " + e.getMessage());
        }
    }

    @Override
    public double getBalance(UUID uuid, String currency) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balance FROM `" + table + "` WHERE uuid=? AND currency=?");
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getDouble(1);
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL getBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] Unexpected error in getBalance for " + uuid + " (" + currency + "): " + e.getMessage());
            }
            return 0.0;
        }
    }

    @Override
    public void setBalance(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement("REPLACE INTO `" + table + "` (uuid, currency, balance) VALUES (?, ?, ?)");
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL setBalance failed for " + uuid + " (" + currency + "): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] Unexpected error in setBalance for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    @Override
    public boolean tryWithdraw(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "UPDATE `" + table + "` SET balance = balance - ? WHERE uuid=? AND currency=? AND balance >= ?"
                );
                ps.setDouble(1, amount);
                ps.setString(2, uuid.toString());
                ps.setString(3, currency);
                ps.setDouble(4, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL tryWithdraw failed for " + uuid + " (" + currency + "): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] Unexpected error in tryWithdraw for " + uuid + " (" + currency + "): " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public void deposit(UUID uuid, String currency, double amount) {
        synchronized (lock) {
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `" + table + "` (uuid, currency, balance) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, currency);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL deposit failed for " + uuid + " (" + currency + "): " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("[EzEconomy] Unexpected error in deposit for " + uuid + " (" + currency + "): " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        synchronized (lock) {
            try {
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
                PreparedStatement ps = connection.prepareStatement("SELECT uuid, balance FROM `" + table + "` WHERE currency=?");
                ps.setString(1, currency);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString(1));
                        double bal = rs.getDouble(2);
                        map.put(uuid, bal);
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[EzEconomy] MySQL getAllBalances failed (" + currency + "): " + e.getMessage());
            }
            return map;
        }
    }
    // --- Bank support ---
    private void ensureBankTables() {
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS banks (name VARCHAR(64), currency VARCHAR(32), balance DOUBLE, PRIMARY KEY (name, currency))");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bank_members (bank VARCHAR(64), uuid VARCHAR(36), owner BOOLEAN, PRIMARY KEY (bank, uuid))");
        } catch (SQLException e) {
            plugin.getLogger().severe("[EzEconomy] MySQL ensureBankTables failed: " + e.getMessage());
        }
    }

    public boolean createBank(String name, UUID owner) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO banks (name, currency, balance) VALUES (?, ?, 0.0)");
                ps.setString(1, name);
                ps.setString(2, "dollar"); // default currency
                ps.executeUpdate();
                ps = connection.prepareStatement("INSERT INTO bank_members (bank, uuid, owner) VALUES (?, ?, ?)");
                ps.setString(1, name);
                ps.setString(2, owner.toString());
                ps.setBoolean(3, true);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                return false;
            }
        }
    }

    public boolean deleteBank(String name) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("DELETE FROM banks WHERE name=?");
                ps.setString(1, name);
                int affected = ps.executeUpdate();
                ps = connection.prepareStatement("DELETE FROM bank_members WHERE bank=?");
                ps.setString(1, name);
                ps.executeUpdate();
                return affected > 0;
            } catch (SQLException e) {
                return false;
            }
        }
    }

    public boolean bankExists(String name) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT name FROM banks WHERE name=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                return false;
            }
        }
    }

    public double getBankBalance(String name, String currency) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT balance FROM banks WHERE name=? AND currency=?");
                ps.setString(1, name);
                ps.setString(2, currency);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getDouble(1);
            } catch (SQLException e) {}
            return 0.0;
        }
    }

    public void setBankBalance(String name, String currency, double amount) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("REPLACE INTO banks (name, currency, balance) VALUES (?, ?, ?)");
                ps.setString(1, name);
                ps.setString(2, currency);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {}
        }
    }

    @Override
    public boolean tryWithdrawBank(String name, String currency, double amount) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "UPDATE banks SET balance = balance - ? WHERE name=? AND currency=? AND balance >= ?"
                );
                ps.setDouble(1, amount);
                ps.setString(2, name);
                ps.setString(3, currency);
                ps.setDouble(4, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                return false;
            }
        }
    }

    @Override
    public void depositBank(String name, String currency, double amount) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO banks (name, currency, balance) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)"
                );
                ps.setString(1, name);
                ps.setString(2, currency);
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
            }
        }
    }

    public Set<String> getBanks() {
        synchronized (lock) {
            ensureBankTables();
            Set<String> set = ConcurrentHashMap.newKeySet();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT name FROM banks");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) set.add(rs.getString(1));
            } catch (SQLException e) {}
            return set;
        }
    }

    public boolean isBankOwner(String name, UUID uuid) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT owner FROM bank_members WHERE bank=? AND uuid=?");
                ps.setString(1, name);
                ps.setString(2, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getBoolean(1);
            } catch (SQLException e) {}
            return false;
        }
    }

    public boolean isBankMember(String name, UUID uuid) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM bank_members WHERE bank=? AND uuid=?");
                ps.setString(1, name);
                ps.setString(2, uuid.toString());
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (SQLException e) {}
            return false;
        }
    }

    public boolean addBankMember(String name, UUID uuid) {
        synchronized (lock) {
            ensureBankTables();
            if (isBankMember(name, uuid)) return false;
            try {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO bank_members (bank, uuid, owner) VALUES (?, ?, ?)");
                ps.setString(1, name);
                ps.setString(2, uuid.toString());
                ps.setBoolean(3, false);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) { return false; }
        }
    }

    public boolean removeBankMember(String name, UUID uuid) {
        synchronized (lock) {
            ensureBankTables();
            try {
                PreparedStatement ps = connection.prepareStatement("DELETE FROM bank_members WHERE bank=? AND uuid=?");
                ps.setString(1, name);
                ps.setString(2, uuid.toString());
                int affected = ps.executeUpdate();
                return affected > 0;
            } catch (SQLException e) { return false; }
        }
    }

    public Set<UUID> getBankMembers(String name) {
        synchronized (lock) {
            ensureBankTables();
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM bank_members WHERE bank=?");
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try { set.add(UUID.fromString(rs.getString(1))); } catch (IllegalArgumentException ignored) {}
                }
            } catch (SQLException e) {}
            return set;
        }
    }
}
