package com.skyblockexp.ezeconomy.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class PostTransactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID source;
    private final UUID target; // may be null
    private final BigDecimal amount;
    private final TransactionType type;

    private final boolean success;

    private final BigDecimal sourceBefore;
    private final BigDecimal sourceAfter;
    private final BigDecimal targetBefore;
    private final BigDecimal targetAfter;

    public PostTransactionEvent(UUID source, UUID target, BigDecimal amount, TransactionType type,
                                boolean success,
                                BigDecimal sourceBefore, BigDecimal sourceAfter,
                                BigDecimal targetBefore, BigDecimal targetAfter) {
        this.source = source;
        this.target = target;
        this.amount = amount;
        this.type = type;
        this.success = success;
        this.sourceBefore = sourceBefore;
        this.sourceAfter = sourceAfter;
        this.targetBefore = targetBefore;
        this.targetAfter = targetAfter;
    }

    public UUID getSource() { return source; }
    public UUID getTarget() { return target; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public boolean isSuccess() { return success; }

    public BigDecimal getSourceBefore() { return sourceBefore; }
    public BigDecimal getSourceAfter() { return sourceAfter; }
    public BigDecimal getTargetBefore() { return targetBefore; }
    public BigDecimal getTargetAfter() { return targetAfter; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
