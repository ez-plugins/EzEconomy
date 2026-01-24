package com.skyblockexp.ezeconomy.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class PreTransactionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID source;
    private final UUID target; // may be null for single-account ops
    private final BigDecimal amount;
    private final TransactionType type;

    private boolean cancelled;
    private String cancelReason;

    public PreTransactionEvent(UUID source, UUID target, BigDecimal amount, TransactionType type) {
        this.source = source;
        this.target = target;
        this.amount = amount;
        this.type = type;
        this.cancelled = false;
    }

    public UUID getSource() {
        return source;
    }

    public UUID getTarget() {
        return target;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public String getCancelReason() { return cancelReason; }

    public void setCancelReason(String reason) { this.cancelReason = reason; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
