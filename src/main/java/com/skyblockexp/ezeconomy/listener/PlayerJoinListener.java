package com.skyblockexp.ezeconomy.listener;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.manager.DailyRewardManager;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.Map;
import java.util.UUID;

public class PlayerJoinListener implements Listener {
    private final EzEconomyPlugin plugin;
    private final DailyRewardManager manager;

    public PlayerJoinListener(EzEconomyPlugin plugin, DailyRewardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());

        autoCreateBank(event.getPlayer());

        persistPlayerInfo(event.getPlayer());
        deliverPendingNotifications(event.getPlayer());

        // Optionally ensure player is stored in the configured storage backend
        if (!plugin.getConfig().getBoolean("store-on-join.enabled", false)) {
            return;
        }

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) return;

        String currency = plugin.getDefaultCurrency();
        try {
            UUID uuid = event.getPlayer().getUniqueId();
            if (!storage.playerExists(uuid)) {
                com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
                if (lm != null) {
                    String token = null;
                    try {
                        token = lm.acquire(uuid, 5000L, 50L, 100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        token = null;
                    }
                    if (token != null) {
                        try {
                            storage.setBalance(uuid, currency, 0.0);
                        } finally {
                            lm.release(uuid, token);
                        }
                    } else {
                        java.util.concurrent.locks.ReentrantLock l = com.skyblockexp.ezeconomy.storage.TransferLockManager.getLock(uuid);
                        l.lock();
                        try {
                            storage.setBalance(uuid, currency, 0.0);
                        } finally {
                            l.unlock();
                        }
                    }
                } else {
                    storage.setBalance(uuid, currency, 0.0);
                }
                plugin.getLogger().info("Stored player " + event.getPlayer().getName() + " on join");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to ensure player stored on join: " + e.getMessage());
        }
    }

    private void autoCreateBank(org.bukkit.entity.Player player) {
        if (!plugin.getConfig().getBoolean("banking.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("banking.auto-create-on-join", true)) return;

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) return;

        final String bankName = player.getName();
        if (storage.bankExists(bankName)) return;

        final UUID ownerUuid = player.getUniqueId();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                storage.createBank(bankName, ownerUuid);
                plugin.getLogger().fine("[EzEconomy] Auto-created bank '" + bankName + "' for " + player.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[EzEconomy] Failed to auto-create bank for " + bankName + ": " + e.getMessage());
            }
        });
    }

    private void persistPlayerInfo(org.bukkit.entity.Player player) {
        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) return;
        try {
            storage.persistPlayerInfo(player.getUniqueId(), player.getName(), player.getDisplayName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist player info for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void deliverPendingNotifications(org.bukkit.entity.Player player) {
        com.skyblockexp.ezeconomy.messaging.MessagingService ms = plugin.getMessagingService();
        if (ms == null) return;
        if (!plugin.getConfig().getBoolean("cross-server.enabled", false)) return;
        com.skyblockexp.ezeconomy.util.scheduler.PlatformScheduler.runTaskLater(plugin, () -> {
            ms.deliverPendingNotifications(player);
        }, 40L);
    }
}
