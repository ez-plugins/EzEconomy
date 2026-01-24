# BankPostTransactionEvent

Description
- Fired after a bank deposit or withdrawal has been applied. This event is informational and intended for logging, auditing, or follow-up actions.

When it's fired
- Storage providers fire this event synchronously on the main server thread immediately after a successful bank balance mutation (methods: `depositBank`, `tryWithdrawBank`).

Semantics
- Non-cancellable. Contains before/after balance snapshots and whether the operation succeeded.

Key fields
- `bankName` — the bank identifier (String).
- `actor` — UUID of the actor initiating the action, or `null` for system-initiated actions.
- `amount` — the amount involved as `BigDecimal`.
- `type` — `TransactionType.BANK_DEPOSIT` or `TransactionType.BANK_WITHDRAW`.
- `success` — boolean indicating whether the operation succeeded.
- `before` — `BigDecimal` bank balance before the operation.
- `after` — `BigDecimal` bank balance after the operation.

Example listener (Java)
```
@EventHandler
public void onBankPost(BankPostTransactionEvent e) {
    if (e.isSuccess()) {
        plugin.getLogger().info("Bank " + e.getBankName() + " changed: " + e.getBefore() + " -> " + e.getAfter());
    }
}
```

Notes
- Post events are useful for audit logs, metrics, or triggering external hooks once a bank operation completes.
