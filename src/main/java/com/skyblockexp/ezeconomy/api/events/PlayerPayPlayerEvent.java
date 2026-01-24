package com.skyblockexp.ezeconomy.api.events;

import java.math.BigDecimal;
import java.util.UUID;

public class PlayerPayPlayerEvent extends PreTransactionEvent {
    public PlayerPayPlayerEvent(UUID payer, UUID payee, BigDecimal amount) {
        super(payer, payee, amount, TransactionType.PAY);
    }
}
