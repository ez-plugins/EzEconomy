package com.skyblockexp.ezeconomy.util.scheduler;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

    private static final boolean FOLIA = detectFolia();
    private static final SchedulerAdapter ADAPTER = FOLIA
            ? new FoliaSchedulerAdapter()
            : new BukkitSchedulerAdapter();

    private PlatformScheduler() {}

    /** Returns {@code true} when the server is running Folia. */
    public static boolean isFolia() {
        return FOLIA;
    }

    // -------------------------------------------------------------------------
    // Convenience static helpers
    // -------------------------------------------------------------------------

    public static ScheduledTask runTask(JavaPlugin plugin, Runnable task) {
        return ADAPTER.runTask(plugin, task);
    }

    public static ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        return ADAPTER.runTaskLater(plugin, task, delayTicks);
    }

    public static ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return ADAPTER.runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task) {
        return ADAPTER.runTaskAsync(plugin, task);
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
        if (plugin.getServer().isPrimaryThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }
        if (FOLIA) {
            CompletableFuture<T> future = new CompletableFuture<>();
            ADAPTER.runTask(plugin, () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future.get();
        } else {
            Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, callable);
            return future.get();
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
