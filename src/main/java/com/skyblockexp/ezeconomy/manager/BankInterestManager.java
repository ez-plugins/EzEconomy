package com.skyblockexp.ezeconomy.manager;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.scheduler.PlatformScheduler;
import com.skyblockexp.ezeconomy.util.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BankInterestManager {
    private final EzEconomyPlugin plugin;
    private ScheduledTask task = null;

    // THREAD SAFETY NOTE:
    // payInterestToAll() is called from a BukkitRunnable (main server thread),
    // so storage operations are not concurrent by default. However, if storage
    // providers are accessed from async tasks elsewhere, or if future changes
    // introduce async interest payout, all storage operations here must be thread-safe.
    //
    // If you plan to run payInterestToAll() asynchronously, ensure:
    // 1. All storageProvider methods (get/setBalance, getBankBalance, getBankMembers, etc.) are thread-safe.
    // 2. Use synchronization or locks if the underlying storage is not thread-safe.
    // 3. Consider using Bukkit's scheduler to run only thread-safe code async, and all Bukkit API calls sync.

    public BankInterestManager(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(long intervalTicks) {
        if (task != null) {
            task.cancel();
        }
        task = PlatformScheduler.runTaskTimer(plugin, this::payInterestToAll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void payInterestToAll() {
        com.skyblockexp.ezeconomy.api.storage.StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            return;
        }
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        boolean multiEnabled = config.getBoolean("multi-currency.enabled", false);
        Set<String> currencies;
        if (multiEnabled) {
            var section = config.getConfigurationSection("multi-currency.currencies");
            currencies = section != null ? section.getKeys(false) : java.util.Collections.singleton("dollar");
        } else {
            currencies = java.util.Collections.singleton("dollar");
        }
        for (String currency : currencies) {
            for (String bank : storage.getBanks()) {
                double bankBalance = storage.getBankBalance(bank, currency);
                Set<UUID> members = storage.getBankMembers(bank);
                if (members == null || members.isEmpty()) continue;
                double grossInterest = calculateInterest(bankBalance);
                double perMemberInterest = grossInterest / members.size();
                for (UUID uuid : members) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    if (perMemberInterest > 0) {
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
                                    storage.setBalance(uuid, currency, storage.getBalance(uuid, currency) + perMemberInterest);
                                } finally {
                                    lm.release(uuid, token);
                                }
                            } else {
                                // fallback to local lock
                                java.util.concurrent.locks.ReentrantLock l = com.skyblockexp.ezeconomy.storage.TransferLockManager.getLock(uuid);
                                l.lock();
                                try {
                                    storage.setBalance(uuid, currency, storage.getBalance(uuid, currency) + perMemberInterest);
                                } finally {
                                    l.unlock();
                                }
                            }
                        } else {
                            storage.setBalance(uuid, currency, storage.getBalance(uuid, currency) + perMemberInterest);
                        }
                        if (player.isOnline()) {
                            String compact = plugin.getCurrencyFormatter().formatShort(perMemberInterest, null);
                            player.getPlayer().sendMessage("You received " + compact + " " + currency + " interest from bank '" + bank + "'");
                        }
                    }
                }
            }
        }
    }

    // Example interest calculation (1% per payout)
    private double calculateInterest(double balance) {
        return Math.round(balance * 0.01 * 100.0) / 100.0;
    }
}
