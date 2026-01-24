package com.skyblockexp.ezeconomy.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;

public class BankPostTransactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String bankName;
    private final java.util.UUID actor;
    private final BigDecimal amount;
    private final TransactionType type;
    private final boolean success;

    private final BigDecimal before;
    private final BigDecimal after;

    public BankPostTransactionEvent(String bankName, java.util.UUID actor, BigDecimal amount, TransactionType type, boolean success, BigDecimal before, BigDecimal after) {
        this.bankName = bankName;
        this.actor = actor;
        this.amount = amount;
        this.type = type;
        this.success = success;
        this.before = before;
        this.after = after;
    }

    public String getBankName() { return bankName; }
    public java.util.UUID getActor() { return actor; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public boolean isSuccess() { return success; }
    public BigDecimal getBefore() { return before; }
    public BigDecimal getAfter() { return after; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
