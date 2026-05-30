package com.skyblockexp.ezeconomy.storage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages JDBC fallback connection and an optional HikariCP pooled datasource.
 * Keeps lifecycle responsibilities out of the main provider class.
 */
public class MySQLConnectionManager {
    private final EzEconomyPlugin plugin;
    private final YamlConfiguration dbConfig;
    private HikariDataSource dataSource;
    private Connection fallbackConnection;

    public MySQLConnectionManager(EzEconomyPlugin plugin, YamlConfiguration dbConfig) {
        this.plugin = plugin;
        this.dbConfig = dbConfig;
    }

    public void init() throws SQLException {
        String host = dbConfig.getString("mysql.host");
        int port = dbConfig.getInt("mysql.port");
        String database = dbConfig.getString("mysql.database");
        String username = dbConfig.getString("mysql.username");
        String password = dbConfig.getString("mysql.password");
        String jdbcUrl = buildJdbcUrl(host, port, database);

        // Create fallback (DriverManager) connection
        if (fallbackConnection != null) {
            try { if (!fallbackConnection.isClosed()) fallbackConnection.close(); } catch (Exception ignored) {}
            fallbackConnection = null;
        }
        fallbackConnection = DriverManager.getConnection(jdbcUrl, username, password);

        // Init pool if enabled
        initPool(jdbcUrl, username, password);
    }

    private void initPool(String jdbcUrl, String username, String password) {
        boolean enabled = plugin.getConfig().getBoolean("performance.mysql.pool.enabled", dbConfig.getBoolean("mysql.pool.enabled", true));
        if (!enabled) {
            if (dataSource != null) dataSource.close();
            dataSource = null;
            return;
        }
        if (dataSource != null) dataSource.close();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EzEconomy-MySQL");
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(Math.max(2, plugin.getConfig().getInt("performance.mysql.pool.maximum-pool-size", dbConfig.getInt("mysql.pool.maximum-pool-size", 32))));
        cfg.setMinimumIdle(Math.max(1, plugin.getConfig().getInt("performance.mysql.pool.minimum-idle", dbConfig.getInt("mysql.pool.minimum-idle", 8))));
        cfg.setConnectionTimeout(Math.max(1000L, plugin.getConfig().getLong("performance.mysql.pool.connection-timeout-ms", dbConfig.getLong("mysql.pool.connection-timeout-ms", 8000L))));
        cfg.setIdleTimeout(Math.max(30000L, plugin.getConfig().getLong("performance.mysql.pool.idle-timeout-ms", dbConfig.getLong("mysql.pool.idle-timeout-ms", 240000L))));
        cfg.setMaxLifetime(Math.max(60000L, plugin.getConfig().getLong("performance.mysql.pool.max-lifetime-ms", dbConfig.getLong("mysql.pool.max-lifetime-ms", 1200000L))));
        cfg.setAutoCommit(plugin.getConfig().getBoolean("performance.mysql.pool.auto-commit", dbConfig.getBoolean("mysql.pool.auto-commit", true)));
        // Optional Hikari tuning parameters
        long leakMs = plugin.getConfig().getLong("performance.mysql.pool.leak-detection-threshold-ms", dbConfig.getLong("mysql.pool.leak-detection-threshold-ms", 0L));
        if (leakMs > 0) cfg.setLeakDetectionThreshold(leakMs);
        long validationTimeout = plugin.getConfig().getLong("performance.mysql.pool.validation-timeout-ms", dbConfig.getLong("mysql.pool.validation-timeout-ms", 500L));
        if (validationTimeout > 0) cfg.setValidationTimeout(validationTimeout);
        long initFail = plugin.getConfig().getLong("performance.mysql.pool.initialization-fail-timeout-ms", dbConfig.getLong("mysql.pool.initialization-fail-timeout-ms", 1L));
        cfg.setInitializationFailTimeout(initFail);
        dataSource = new HikariDataSource(cfg);
    }

    private String buildJdbcUrl(String host, int port, String database) {
        String params = plugin.getConfig().getString("performance.mysql.jdbc-params",
                dbConfig.getString("mysql.jdbc-params",
                "useSSL=false&serverTimezone=UTC&cachePrepStmts=true&prepStmtCacheSize=1024&prepStmtCacheSqlLimit=4096&useServerPrepStmts=true&elideSetAutoCommits=true&maintainTimeStats=false&useLocalSessionState=true&rewriteBatchedStatements=true&cacheResultSetMetadata=true&cacheServerConfiguration=true&tcpKeepAlive=true&connectTimeout=8000&socketTimeout=30000"));
        if (params == null || params.trim().isEmpty()) {
            return "jdbc:mysql://" + host + ":" + port + "/" + database;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params;
    }

    public Connection getFallbackConnection() {
        return fallbackConnection;
    }

    public HikariDataSource getHikariDataSource() {
        return dataSource;
    }

    public void close() {
        try { if (dataSource != null) dataSource.close(); } catch (Exception ignored) {}
        dataSource = null;
        try { if (fallbackConnection != null && !fallbackConnection.isClosed()) fallbackConnection.close(); } catch (Exception ignored) {}
        fallbackConnection = null;
    }
}
