package com.skyblockexp.ezeconomy.compat.scheduler;

import com.skyblockexp.ezeconomy.util.scheduler.BukkitSchedulerAdapter;
import com.skyblockexp.ezeconomy.util.scheduler.FoliaSchedulerAdapter;
import com.skyblockexp.ezeconomy.util.scheduler.ScheduledTask;
import com.skyblockexp.ezeconomy.util.scheduler.SchedulerAdapter;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitFoliaSchedulerCompat implements SchedulerCompat {
    private final boolean folia = detectFolia();
    private final SchedulerAdapter adapter = folia ? new FoliaSchedulerAdapter() : new BukkitSchedulerAdapter();

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public ScheduledTask runTask(JavaPlugin plugin, Runnable task) {
        return adapter.runTask(plugin, task);
    }

    @Override
    public ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        return adapter.runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return adapter.runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task) {
        return adapter.runTaskAsync(plugin, task);
    }

    @Override
    public <T> T callSync(JavaPlugin plugin, Callable<T> callable) throws ExecutionException, InterruptedException {
        if (plugin.getServer().isPrimaryThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }
        if (folia) {
            CompletableFuture<T> future = new CompletableFuture<>();
            runTask(plugin, () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future.get();
        }
        Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, callable);
        return future.get();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
