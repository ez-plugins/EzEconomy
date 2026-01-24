# BankPreTransactionEvent

Description
- Fired before a bank deposit or withdrawal is applied. This is a cancellable event intended for other plugins to validate or prevent bank changes.

When it's fired
- Storage providers fire this event synchronously on the main server thread immediately before modifying a bank's balance (methods: `depositBank`, `tryWithdrawBank`).

Semantics
- Cancellable: listeners may call `setCancelled(true)` to block the operation.
- `getCancelReason()` (if provided by listeners) may be used by callers to display a message; implementations may read this after cancellation.

Key fields
- `bankName` — the bank identifier (String).
- `actor` — UUID of the actor initiating the action, or `null` for system-initiated actions.
- `amount` — the amount involved as `BigDecimal`.
- `type` — `TransactionType.BANK_DEPOSIT` or `TransactionType.BANK_WITHDRAW`.

Example listener (Java)
```
@EventHandler
public void onBankPre(BankPreTransactionEvent e) {
    if (e.getType() == TransactionType.BANK_WITHDRAW && e.getAmount().compareTo(new BigDecimal("10000")) > 0) {
        e.setCancelled(true);
        e.setCancelReason("Withdrawals over 10k must be approved");
    }
}
```

Notes
- Storage implementations fire this event from within their bank methods and will abort the operation when the event is cancelled.
