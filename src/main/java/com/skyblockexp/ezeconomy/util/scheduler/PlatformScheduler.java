package com.skyblockexp.ezeconomy.util.scheduler;

import com.skyblockexp.ezeconomy.compat.scheduler.BukkitFoliaSchedulerCompat;
import com.skyblockexp.ezeconomy.compat.scheduler.SchedulerCompat;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Static factory and convenience façade for the server-appropriate scheduler.
 *
 * <p>At first use the factory detects whether the server is running Folia by
 * checking for {@code io.papermc.paper.threadedregions.RegionizedServer}.
 * If found it returns a {@link FoliaSchedulerAdapter}; otherwise it returns a
 * {@link BukkitSchedulerAdapter} (compatible with Spigot, Paper, Purpur).
 *
 * <p>Call the static helpers directly — no need to obtain the adapter instance:
 * <pre>{@code
 * PlatformScheduler.runTaskLater(plugin, () -> ..., 20L);
 * ScheduledTask handle = PlatformScheduler.runTaskTimer(plugin, () -> ..., 0L, 200L);
 * handle.cancel();
 * }</pre>
 */
public final class PlatformScheduler {

    private static final SchedulerCompat COMPAT = new BukkitFoliaSchedulerCompat();

    private PlatformScheduler() {}

    /** Returns {@code true} when the server is running Folia. */
    public static boolean isFolia() {
        return COMPAT.isFolia();
    }

    // -------------------------------------------------------------------------
    // Convenience static helpers
    // -------------------------------------------------------------------------

    public static ScheduledTask runTask(JavaPlugin plugin, Runnable task) {
        return COMPAT.runTask(plugin, task);
    }

    public static ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        return COMPAT.runTaskLater(plugin, task, delayTicks);
    }

    public static ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return COMPAT.runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task) {
        return COMPAT.runTaskAsync(plugin, task);
    }

    /**
     * Schedules {@code callable} on the main/global-region thread and blocks the calling thread
     * until it completes, returning the result — a portable replacement for
     * {@code BukkitScheduler.callSyncMethod}.
     *
     * <p>If already on the primary thread the callable is executed immediately without scheduling.
     *
     * @throws ExecutionException   if the callable threw an exception
     * @throws InterruptedException if the calling thread was interrupted while waiting
     */
    public static <T> T callSync(JavaPlugin plugin, Callable<T> callable)
            throws ExecutionException, InterruptedException {
        return COMPAT.callSync(plugin, callable);
    }
}
