package com.skyblockexp.ezeconomy.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class BankPreTransactionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String bankName;
    private final UUID actor; // initiator (may be null for system)
    private final BigDecimal amount;
    private final TransactionType type;

    private boolean cancelled;
    private String cancelReason;

    public BankPreTransactionEvent(String bankName, UUID actor, BigDecimal amount, TransactionType type) {
        this.bankName = bankName;
        this.actor = actor;
        this.amount = amount;
        this.type = type;
    }

    public String getBankName() { return bankName; }
    public UUID getActor() { return actor; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String reason) { this.cancelReason = reason; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
