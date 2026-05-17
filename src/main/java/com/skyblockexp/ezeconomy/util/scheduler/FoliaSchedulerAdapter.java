package com.skyblockexp.ezeconomy.util.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * {@link SchedulerAdapter} backed by Folia's region-aware schedulers.
 *
 * <p>This class is only instantiated when {@link PlatformScheduler} detects that
 * the server is running Folia. On non-Folia runtimes this class is never loaded,
 * so there is no risk of {@link NoClassDefFoundError} from the Folia-specific
 * imports above.
 *
 * <ul>
 *   <li>Tasks not tied to a world position → {@link GlobalRegionScheduler}
 *   <li>Async tasks → {@link AsyncScheduler}
 * </ul>
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    @Override
    public ScheduledTask runTask(JavaPlugin plugin, Runnable task) {
        GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
        io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                scheduler.run(plugin, st -> task.run());
        return t::cancel;
    }

    @Override
    public ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
        // Folia requires at least 1 tick for delayed tasks
        long delay = Math.max(1L, delayTicks);
        io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                scheduler.runDelayed(plugin, st -> task.run(), delay);
        return t::cancel;
    }

    @Override
    public ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
        long delay  = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                scheduler.runAtFixedRate(plugin, st -> task.run(), delay, period);
        return t::cancel;
    }

    @Override
    public ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task) {
        AsyncScheduler scheduler = Bukkit.getAsyncScheduler();
        io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                scheduler.runNow(plugin, st -> task.run());
        return t::cancel;
    }
}
