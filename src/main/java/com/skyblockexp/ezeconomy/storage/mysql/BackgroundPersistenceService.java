package com.skyblockexp.ezeconomy.storage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariDataSource;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;

/**
 * Standalone background persistence worker for non-critical writes such as
 * player metadata. Bounded queue to avoid unbounded memory growth.
 */
public class BackgroundPersistenceService {
    private final BlockingQueue<PlayerSave> queue;
    private final Thread worker;
    private volatile boolean running = true;
    private final EzEconomyPlugin plugin;
    private final HikariDataSource pool;

    public BackgroundPersistenceService(EzEconomyPlugin plugin, HikariDataSource pool, int queueSize) {
        this.plugin = plugin;
        this.pool = pool;
        this.queue = new LinkedBlockingQueue<>(Math.max(128, queueSize));
        this.worker = new Thread(this::runLoop, "EzEconomy-BackgroundPersistence");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void submitPlayerSave(UUID uuid, String name, String displayName) {
        PlayerSave ps = new PlayerSave(uuid, name, displayName);
        if (!queue.offer(ps)) {
            plugin.getLogger().warning("[EzEconomy] Background persistence queue full; dropping player save for " + uuid);
        }
    }

    public void shutdown() {
        running = false;
        worker.interrupt();
    }

    private void runLoop() {
        while (running || !queue.isEmpty()) {
            try {
                PlayerSave p = queue.poll(1, TimeUnit.SECONDS);
                if (p == null) continue;
                persist(p);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                plugin.getLogger().warning("[EzEconomy] Background persistence error: " + t.getMessage());
            }
        }
    }

    private void persist(PlayerSave p) {
        String sql = "INSERT INTO players (id, name, displayName) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), displayName = VALUES(displayName)";
        if (pool != null) {
            try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, p.uuid.toString());
                ps.setString(2, p.name);
                ps.setString(3, p.displayName);
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                plugin.getLogger().warning("[EzEconomy] Background player save failed (pooled): " + e.getMessage());
            }
        }
        // If pool unavailable, try DriverManager fallback (best-effort)
        // Pool-less fallback must be invoked by caller (provider) which holds a connection reference.
    }

    private static class PlayerSave {
        final UUID uuid;
        final String name;
        final String displayName;

        PlayerSave(UUID uuid, String name, String displayName) {
            this.uuid = uuid;
            this.name = name;
            this.displayName = displayName;
        }
    }
}
