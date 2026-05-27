package com.skyblockexp.ezeconomy.compat.scheduler;

import com.skyblockexp.ezeconomy.util.scheduler.ScheduledTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.bukkit.plugin.java.JavaPlugin;

public interface SchedulerCompat {
    boolean isFolia();
    ScheduledTask runTask(JavaPlugin plugin, Runnable task);
    ScheduledTask runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks);
    ScheduledTask runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks);
    ScheduledTask runTaskAsync(JavaPlugin plugin, Runnable task);
    <T> T callSync(JavaPlugin plugin, Callable<T> callable) throws ExecutionException, InterruptedException;
}
