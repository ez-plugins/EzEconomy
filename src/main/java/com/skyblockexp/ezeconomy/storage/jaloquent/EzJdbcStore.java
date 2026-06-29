package com.skyblockexp.ezeconomy.storage.jaloquent;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.exception.TransactionException;
import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.jaloquent.store.sql.TransactionalJdbcStore;

import java.sql.Connection;
import java.sql.SQLNonTransientConnectionException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges a JDBC {@link Connection} to the Jaloquent {@link DataStore} and
 * {@link JdbcStore} interfaces, enabling {@link com.github.ezframework.jaloquent.model.ModelRepository}
 * to drive SQL operations for MySQL.
 *
 * <p>The flat-map {@link DataStore} methods ({@code save}, {@code load},
 * {@code delete}, {@code exists}) are intentionally unsupported: all access
 * goes through the SQL path activated when a {@link com.github.ezframework.jaloquent.model.TableRegistry}
 * entry exists and the store implements {@link JdbcStore}.
 *
 * <p>Subclasses may override {@link #transformSql(String)} to adapt
 * dialect-specific syntax (e.g. SQLite).
 */
public class EzJdbcStore implements DataStore, JdbcStore, TransactionalJdbcStore {

    private volatile Connection connection;
    private final ConnectionRefresher connectionRefresher;

    @FunctionalInterface
    public interface ConnectionRefresher {
        Connection refresh() throws Exception;
    }

    public EzJdbcStore(Connection connection) {
        this(connection, null);
    }

    public EzJdbcStore(Connection connection, ConnectionRefresher connectionRefresher) {
        this.connection = connection;
        this.connectionRefresher = connectionRefresher;
    }

    // -------------------------------------------------------------------------
    // JdbcStore
    // -------------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) throws Exception {
        String transformed = transformSql(sql);
        try {
            return doQuery(transformed, params);
        } catch (SQLException first) {
            if (!tryRecoverConnection(first)) {
                throw first;
            }
            try {
                return doQuery(transformed, params);
            } catch (SQLException retry) {
                retry.addSuppressed(first);
                throw retry;
            }
        }
    }

    @Override
    public int executeUpdate(String sql, List<Object> params) throws Exception {
        String transformed = transformSql(sql);
        try {
            return doExecuteUpdate(transformed, params);
        } catch (SQLException first) {
            if (!tryRecoverConnection(first)) {
                throw first;
            }
            try {
                return doExecuteUpdate(transformed, params);
            } catch (SQLException retry) {
                retry.addSuppressed(first);
                throw retry;
            }
        }
    }

    private List<Map<String, Object>> doQuery(String transformed, List<Object> params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(transformed)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private int doExecuteUpdate(String transformed, List<Object> params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(transformed)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        }
    }

    private boolean tryRecoverConnection(SQLException failure) {
        if (connectionRefresher == null || !isConnectionFailure(failure)) {
            return false;
        }
        try {
            if (connection != null && !connection.getAutoCommit()) {
                return false;
            }
        } catch (SQLException ignored) {
            // Continue; connection likely already broken.
        }
        synchronized (this) {
            try {
                if (isConnectionUsable(connection)) {
                    return true;
                }
                Connection refreshed = connectionRefresher.refresh();
                if (refreshed == null) {
                    return false;
                }
                this.connection = refreshed;
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private boolean isConnectionUsable(Connection candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            return !candidate.isClosed() && candidate.isValid(2);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isConnectionFailure(SQLException ex) {
        if (ex instanceof SQLTransientConnectionException || ex instanceof SQLNonTransientConnectionException) {
            return true;
        }
        String sqlState = ex.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        Throwable cur = ex;
        while (cur != null) {
            String cn = cur.getClass().getName();
            String msg = cur.getMessage();
            if (cn.contains("CommunicationsException") || cn.contains("ConnectionIsClosedException")) {
                return true;
            }
            if (msg != null) {
                if (msg.contains("Communications link failure")
                        || msg.contains("Connection reset")
                        || msg.contains("Connection is closed")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Hook for subclasses to rewrite dialect-specific SQL before execution.
     * The default implementation returns the SQL unchanged (MySQL-compatible).
     *
     * @param sql original SQL string produced by Jaloquent
     * @return transformed SQL ready for this dialect
     */
    protected String transformSql(String sql) {
        return sql;
    }

    // -------------------------------------------------------------------------
    // TransactionalJdbcStore — delegates to the underlying Connection
    // -------------------------------------------------------------------------

    @Override
    public void beginTransaction() throws StorageException {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new TransactionException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commitTransaction() throws StorageException {
        try {
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new TransactionException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollbackTransaction() throws StorageException {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new TransactionException("Failed to roll back transaction", e);
        }
    }

    // -------------------------------------------------------------------------
    // DataStore — unsupported; EzEconomy uses the SQL path exclusively
    // -------------------------------------------------------------------------

    @Override
    public void save(String path, Map<String, Object> data) throws Exception {
        throw new UnsupportedOperationException("EzJdbcStore uses the SQL path only");
    }

    @Override
    public Optional<Map<String, Object>> load(String path) throws Exception {
        throw new UnsupportedOperationException("EzJdbcStore uses the SQL path only");
    }

    @Override
    public void delete(String path) throws Exception {
        throw new UnsupportedOperationException("EzJdbcStore uses the SQL path only");
    }

    @Override
    public boolean exists(String path) throws Exception {
        throw new UnsupportedOperationException("EzJdbcStore uses the SQL path only");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }
}
