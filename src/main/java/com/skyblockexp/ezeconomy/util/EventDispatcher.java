package com.skyblockexp.ezeconomy.util;

import com.skyblockexp.ezeconomy.util.scheduler.PlatformScheduler;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * Central engine for firing Bukkit events from any thread safely.
 *
 * <p>Bukkit requires that {@link org.bukkit.plugin.PluginManager#callEvent(Event)} is called on
 * the server main thread for non-async events. Storage providers run their IO on background
 * threads, so a naive {@code callSyncMethod(...).get()} from a background thread is correct —
 * but the same call <em>from the main thread</em> deadlocks because the scheduled task can never
 * execute while the main thread is blocked waiting for it.
 *
 * <p>{@link #fireSync} encapsulates the correct pattern once, guaranteeing:
 * <ul>
 *   <li>If already on the main thread — call the event directly.</li>
 *   <li>If on a background thread — schedule on the main thread and block until complete.</li>
 * </ul>
 *
 * <p>All storage providers and services <em>must</em> use this class instead of replicating the
 * pattern inline.  Adding the guard to a new event type is a one-line change here rather than
 * a scattered edit across every provider.
 */
public final class EventDispatcher {

    private EventDispatcher() {}

    /**
     * Fires a synchronous Bukkit event on the main server thread, regardless of the calling thread.
     *
     * <p>The event is returned so callers can immediately inspect its state (e.g.
     * {@code isCancelled()}) after the call.
     *
     * @param plugin  the owning plugin (used to schedule back onto the main thread)
     * @param event   the event to fire; must not be null
     * @param <E>     type of the event
     * @return the same event instance, after all handlers have processed it
     */
    public static <E extends Event> E fireSync(JavaPlugin plugin, E event) {
        if (plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getPluginManager().callEvent(event);
        } else {
            try {
                PlatformScheduler.callSync(plugin, () -> {
                    plugin.getServer().getPluginManager().callEvent(event);
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logDispatchFailure(plugin.getLogger(), event, e);
            } catch (ExecutionException e) {
                logDispatchFailure(plugin.getLogger(), event, e);
            }
        }
        return event;
    }

    /**
     * Fires a synchronous Bukkit event and returns whether it was <em>not</em> cancelled.
     *
     * <p>Convenience overload for the common {@code if (fireSync(...).isCancelled()) return}
     * pattern.  Only valid for events that implement {@link org.bukkit.event.Cancellable}.
     *
     * @param plugin  the owning plugin
     * @param event   a cancellable event; must not be null
     * @param <E>     type of the event (must implement {@link org.bukkit.event.Cancellable})
     * @return {@code true} if the event was NOT cancelled; {@code false} if it was cancelled
     */
    public static <E extends Event & org.bukkit.event.Cancellable> boolean fireSyncAndAllow(JavaPlugin plugin, E event) {
        return !fireSync(plugin, event).isCancelled();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static void logDispatchFailure(Logger logger, Event event, Exception cause) {
        logger.warning("[EzEconomy] Failed to dispatch " + event.getClass().getSimpleName()
                + " on the main thread: " + cause.getMessage());
    }
}
