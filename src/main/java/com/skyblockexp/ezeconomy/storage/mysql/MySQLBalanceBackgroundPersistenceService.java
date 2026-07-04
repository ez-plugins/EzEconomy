package com.skyblockexp.ezeconomy.storage.mysql;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Background persistence worker for aggregating and flushing balance deltas to MySQL.
 */
public class MySQLBalanceBackgroundPersistenceService {
    private static final int LOCAL_REPLAY_BATCH_SIZE = 512;
    private final EzEconomyPlugin plugin;
    private final HikariDataSource pool;
    private final String tableName;
    private final BlockingQueue<String> queue;
    private final ConcurrentHashMap<String, PendingEntry> pending;
    private final Thread worker;
    private final String localSpoolDbUrl;
    private volatile boolean running = true;
    private final int batchSize;
    private final long flushIntervalMs;

    public MySQLBalanceBackgroundPersistenceService(EzEconomyPlugin plugin,
                                                   HikariDataSource pool,
                                                   String tableName,
                                                   int queueSize,
                                                   int batchSize,
                                                   long flushIntervalMs) {
        this.plugin = plugin;
        this.pool = pool;
        this.tableName = tableName;
        this.queue = new LinkedBlockingQueue<>(Math.max(128, queueSize));
        this.pending = new ConcurrentHashMap<>();
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(10L, flushIntervalMs);
        File spoolDir = new File(plugin.getDataFolder(), "spool");
        if (!spoolDir.exists()) {
            spoolDir.mkdirs();
        }
        this.localSpoolDbUrl = "jdbc:sqlite:" + new File(spoolDir, "mysql-balance-fallback.db").getAbsolutePath();
        this.worker = new Thread(this::runLoop, "EzEconomy-MySQLBalanceBackground");
        this.worker.setDaemon(true);
        this.worker.start();
        replayLocalSpoolToMySql();
    }

    public int getLocalSpoolSize() {
        ensureLocalSpoolSchema();
        String sql = "SELECT COUNT(*) FROM ezeconomy_mysql_spool";
        try (Connection conn = DriverManager.getConnection(localSpoolDbUrl);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EzEconomy] Failed reading MySQL fallback spool size: " + e.getMessage());
        }
        return -1;
    }

    public ReplayResult replayLocalSpoolNow() {
        return replayLocalSpoolToMySql();
    }

    public void submitBalanceDelta(String id, String uuid, String currency, double delta) {
        PendingEntry entry = pending.compute(id, (k, v) -> {
            if (v == null) {
                PendingEntry p = new PendingEntry(uuid, currency);
                p.adder.add(delta);
                return p;
            }
            v.adder.add(delta);
            if (v.uuid == null && uuid != null) v.uuid = uuid;
            if (v.currency == null && currency != null) v.currency = currency;
            return v;
        });
        queue.offer(id);
        // intentionally no logging here to avoid noisy output during normal operation
    }

    public double peekPendingSum(String id) {
        PendingEntry e = pending.get(id);
        return e == null ? 0.0 : e.adder.sum();
    }

    /**
     * Flush pending deltas for a single id synchronously on the caller thread.
     * Used by withdraw operations to ensure consistency.
     */
    public void flushIdSync(String id) {
        flushIdSyncStrict(id);
    }

    /**
     * Flush pending deltas for a single id synchronously and report whether
     * persistence succeeded. Returns true when no pending delta exists.
     */
    public boolean flushIdSyncStrict(String id) {
        PendingEntry entry = pending.remove(id);
        if (entry == null) return true;
        double delta = entry.adder.sumThenReset();
        if (delta == 0.0) return true;
        return applySingle(id, entry.uuid, entry.currency, delta);
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
        try { worker.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        List<FlushRecord> remaining = drainPendingRecords();
        if (!remaining.isEmpty()) {
            boolean flushed = persistRecordsStrict(remaining, false);
            if (!flushed) {
                appendToLocalSpool(remaining);
            }
        }
        List<FlushRecord> latePending = drainPendingRecords();
        if (!latePending.isEmpty()) {
            appendToLocalSpool(latePending);
        }
    }

    private void runLoop() {
        Set<String> ids = new HashSet<>();
        while (running) {
            try {
                String id = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (id != null) ids.add(id);
                List<String> drained = new ArrayList<>();
                queue.drainTo(drained);
                drained.forEach(ids::add);
                if (ids.isEmpty()) continue;
                List<FlushRecord> batch = new ArrayList<>();
                for (String key : ids) {
                    PendingEntry e = pending.remove(key);
                    if (e == null) continue;
                    double d = e.adder.sumThenReset();
                    if (d == 0.0) continue;
                    batch.add(new FlushRecord(key, e.uuid, e.currency, d));
                    if (batch.size() >= batchSize) break;
                }
                if (!batch.isEmpty()) persistRecords(batch);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                plugin.getLogger().warning("[EzEconomy] MySQL balance background loop error: " + t.getMessage());
            } finally {
                ids.clear();
            }
        }
    }

    private void persistRecords(List<FlushRecord> batch) {
        persistRecordsStrict(batch, true);
    }

    private boolean persistRecordsStrict(List<FlushRecord> batch) {
        return persistRecordsStrict(batch, true);
    }

    private boolean persistRecordsStrict(List<FlushRecord> batch, boolean requeueOnFailure) {
        if (batch == null || batch.isEmpty()) return true;
        String sql = "INSERT INTO " + tableName + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)";
        if (pool == null) {
            plugin.getLogger().warning("[EzEconomy] MySQL balance background persist attempted without pool");
            if (requeueOnFailure) {
                requeueFailedBatch(batch);
            }
            return false;
        }
        try (Connection conn = pool.getConnection()) {
            try {
                conn.setAutoCommit(false);
            } catch (SQLException ignored) {}
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (FlushRecord r : batch) {
                    ps.setString(1, r.id);
                    ps.setString(2, r.uuid);
                    ps.setString(3, r.currency);
                    ps.setDouble(4, r.delta);
                    ps.addBatch();
                }
                ps.executeBatch();
                try { conn.commit(); } catch (SQLException ignored) {}
                return true;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().warning("[EzEconomy] MySQL balance background persist failed: " + e.getMessage());
                if (requeueOnFailure) {
                    requeueFailedBatch(batch);
                }
                return false;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EzEconomy] MySQL balance background persist connection failed: " + e.getMessage());
            if (requeueOnFailure) {
                requeueFailedBatch(batch);
            }
            return false;
        }
    }

    private boolean applySingle(String id, String uuid, String currency, double delta) {
        List<FlushRecord> one = new ArrayList<>();
        one.add(new FlushRecord(id, uuid, currency, delta));
        return persistRecordsStrict(one);
    }

    private void requeueFailedBatch(List<FlushRecord> batch) {
        for (FlushRecord r : batch) {
            pending.compute(r.id, (k, v) -> {
                if (v == null) {
                    PendingEntry p = new PendingEntry(r.uuid, r.currency);
                    p.adder.add(r.delta);
                    return p;
                }
                v.adder.add(r.delta);
                if (v.uuid == null && r.uuid != null) v.uuid = r.uuid;
                if (v.currency == null && r.currency != null) v.currency = r.currency;
                return v;
            });
            if (!queue.offer(r.id)) {
                queue.poll();
                queue.offer(r.id);
            }
        }
    }

    private List<FlushRecord> drainPendingRecords() {
        List<FlushRecord> records = new ArrayList<>();
        for (String id : pending.keySet()) {
            PendingEntry e = pending.remove(id);
            if (e == null) {
                continue;
            }
            double d = e.adder.sumThenReset();
            if (d == 0.0) {
                continue;
            }
            records.add(new FlushRecord(id, e.uuid, e.currency, d));
        }
        return records;
    }

    private void appendToLocalSpool(List<FlushRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        ensureLocalSpoolSchema();
        String insert = "INSERT INTO ezeconomy_mysql_spool (balance_id, uuid, currency, delta, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(localSpoolDbUrl)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                long now = System.currentTimeMillis();
                for (FlushRecord r : batch) {
                    ps.setString(1, r.id);
                    ps.setString(2, r.uuid);
                    ps.setString(3, r.currency);
                    ps.setDouble(4, r.delta);
                    ps.setLong(5, now);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().severe("[EzEconomy] Failed to write MySQL fallback spool: " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[EzEconomy] Failed to open MySQL fallback spool DB: " + e.getMessage());
        }
    }

    private ReplayResult replayLocalSpoolToMySql() {
        ensureLocalSpoolSchema();
        int replayed = 0;
        while (true) {
            List<SpoolRecord> toReplay = readSpoolBatch(LOCAL_REPLAY_BATCH_SIZE);
            if (toReplay.isEmpty()) {
                return new ReplayResult(true, replayed, 0);
            }
            List<FlushRecord> mysqlBatch = new ArrayList<>(toReplay.size());
            for (SpoolRecord r : toReplay) {
                mysqlBatch.add(new FlushRecord(r.balanceId, r.uuid, r.currency, r.delta));
            }
            if (!persistRecordsStrict(mysqlBatch, false)) {
                plugin.getLogger().warning("[EzEconomy] MySQL still unavailable; keeping " + toReplay.size() + " fallback records for next startup");
                int remaining = getLocalSpoolSize();
                return new ReplayResult(false, replayed, Math.max(remaining, toReplay.size()));
            }
            deleteSpoolRows(toReplay);
            replayed += toReplay.size();
        }
    }

    private List<SpoolRecord> readSpoolBatch(int limit) {
        List<SpoolRecord> rows = new ArrayList<>();
        String sql = "SELECT spool_id, balance_id, uuid, currency, delta FROM ezeconomy_mysql_spool ORDER BY spool_id ASC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(localSpoolDbUrl);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SpoolRecord(
                            rs.getLong("spool_id"),
                            rs.getString("balance_id"),
                            rs.getString("uuid"),
                            rs.getString("currency"),
                            rs.getDouble("delta")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EzEconomy] Failed reading MySQL fallback spool: " + e.getMessage());
        }
        return rows;
    }

    private void deleteSpoolRows(List<SpoolRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM ezeconomy_mysql_spool WHERE spool_id = ?";
        try (Connection conn = DriverManager.getConnection(localSpoolDbUrl)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (SpoolRecord r : rows) {
                    ps.setLong(1, r.spoolId);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().warning("[EzEconomy] Failed deleting replayed MySQL spool rows: " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EzEconomy] Failed opening MySQL spool DB for delete: " + e.getMessage());
        }
    }

    private void ensureLocalSpoolSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS ezeconomy_mysql_spool ("
                + "spool_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "balance_id VARCHAR(128) NOT NULL,"
                + "uuid VARCHAR(36),"
                + "currency VARCHAR(32),"
                + "delta DOUBLE NOT NULL,"
                + "created_at BIGINT NOT NULL"
                + ")";
        try (Connection conn = DriverManager.getConnection(localSpoolDbUrl);
             Statement st = conn.createStatement()) {
            st.executeUpdate(ddl);
        } catch (SQLException e) {
            plugin.getLogger().warning("[EzEconomy] Failed creating MySQL fallback spool schema: " + e.getMessage());
        }
    }

    private static class PendingEntry {
        final DoubleAdder adder = new DoubleAdder();
        volatile String uuid;
        volatile String currency;

        PendingEntry(String uuid, String currency) {
            this.uuid = uuid;
            this.currency = currency;
        }
    }

    private static class FlushRecord {
        final String id;
        final String uuid;
        final String currency;
        final double delta;

        FlushRecord(String id, String uuid, String currency, double delta) {
            this.id = id;
            this.uuid = uuid;
            this.currency = currency;
            this.delta = delta;
        }
    }

    private static class SpoolRecord {
        final long spoolId;
        final String balanceId;
        final String uuid;
        final String currency;
        final double delta;

        SpoolRecord(long spoolId, String balanceId, String uuid, String currency, double delta) {
            this.spoolId = spoolId;
            this.balanceId = balanceId;
            this.uuid = uuid;
            this.currency = currency;
            this.delta = delta;
        }
    }

    public static class ReplayResult {
        private final boolean success;
        private final int replayedRows;
        private final int remainingRows;

        public ReplayResult(boolean success, int replayedRows, int remainingRows) {
            this.success = success;
            this.replayedRows = replayedRows;
            this.remainingRows = remainingRows;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getReplayedRows() {
            return replayedRows;
        }

        public int getRemainingRows() {
            return remainingRows;
        }
    }
}
