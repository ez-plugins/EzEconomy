package com.skyblockexp.ezeconomy.util.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@link SchedulerAdapter} backed by the standard Bukkit scheduler.
 * Used on Spigot, Paper, and Purpur (non-Folia) servers.
 */
public final class BukkitSchedulerAdapter implements SchedulerAdapter {

    @Override
    public ScheduledTask runTask(JavaPlugin plugin, Runnable task) {
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTask(plugin, task);
        return t::cancel;
    }

    @Override
    public ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return t::cancel;
    }

    @Override
    public ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return t::cancel;
    }

    @Override
    public ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task) {
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        return t::cancel;
    }
}
