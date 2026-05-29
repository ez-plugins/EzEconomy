package com.skyblockexp.ezeconomy.storage.mysql;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
    private final EzEconomyPlugin plugin;
    private final HikariDataSource pool;
    private final String tableName;
    private final BlockingQueue<String> queue;
    private final ConcurrentHashMap<String, PendingEntry> pending;
    private final Thread worker;
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
        this.worker = new Thread(this::runLoop, "EzEconomy-MySQLBalanceBackground");
        this.worker.setDaemon(true);
        this.worker.start();
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
        PendingEntry entry = pending.remove(id);
        if (entry == null) return;
        double delta = entry.adder.sumThenReset();
        if (delta == 0.0) return;
        applySingle(id, entry.uuid, entry.currency, delta);
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
        try { worker.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        List<FlushRecord> remaining = new ArrayList<>();
        for (String id : pending.keySet()) {
            PendingEntry e = pending.remove(id);
            if (e == null) continue;
            double d = e.adder.sumThenReset();
            if (d == 0.0) continue;
            remaining.add(new FlushRecord(id, e.uuid, e.currency, d));
        }
        if (!remaining.isEmpty()) persistRecords(remaining);
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
        if (batch == null || batch.isEmpty()) return;
        String sql = "INSERT INTO " + tableName + " (id, uuid, currency, balance) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)";
        if (pool == null) {
            plugin.getLogger().warning("[EzEconomy] MySQL balance background persist attempted without pool");
            return;
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
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().warning("[EzEconomy] MySQL balance background persist failed: " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[EzEconomy] MySQL balance background persist connection failed: " + e.getMessage());
        }
    }

    private void applySingle(String id, String uuid, String currency, double delta) {
        List<FlushRecord> one = new ArrayList<>();
        one.add(new FlushRecord(id, uuid, currency, delta));
        persistRecords(one);
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
}
