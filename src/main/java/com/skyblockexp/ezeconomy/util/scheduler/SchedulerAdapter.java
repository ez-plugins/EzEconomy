package com.skyblockexp.ezeconomy.util.scheduler;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Abstraction over Bukkit and Folia scheduling APIs.
 * Implementations must be chosen at startup via {@link PlatformScheduler}.
 */
public interface SchedulerAdapter {

    /**
     * Run {@code task} on the global region (main thread on Bukkit, global region on Folia).
     */
    ScheduledTask runTask(JavaPlugin plugin, Runnable task);

    /**
     * Run {@code task} after {@code delayTicks} ticks on the global region / main thread.
     */
    ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks);

    /**
     * Run {@code task} repeatedly on the global region / main thread.
     *
     * @param delayTicks  initial delay before the first execution
     * @param periodTicks interval between subsequent executions
     */
    ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks);

    /**
     * Run {@code task} asynchronously.
     */
    ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task);
}
