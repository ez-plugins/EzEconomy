package com.skyblockexp.ezeconomy.util.scheduler;

/**
 * Platform-agnostic handle for a scheduled task.
 * Returned by {@link SchedulerAdapter} methods; call {@link #cancel()} to stop the task.
 */
public interface ScheduledTask {
    void cancel();
}
