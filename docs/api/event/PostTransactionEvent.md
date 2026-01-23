# PostTransactionEvent

Class: `com.skyblockexp.ezeconomy.api.events.PostTransactionEvent`

Overview:
- Fired after an attempted transaction completes (successfully or not).
- Not cancellable — this event is informational and intended for logging, metrics, UI updates, and integrations.
- Fired synchronously on the server main thread.

Fields / Accessors:
- `UUID getSource()` — the initiating account (may be `null`).
- `UUID getTarget()` — the target account (may be `null`).
- `BigDecimal getAmount()` — the amount involved in the attempted transaction.
- `TransactionType getType()` — the operation type.
- `boolean isSuccess()` — whether the operation succeeded.
- `BigDecimal getSourceBefore()` / `getSourceAfter()` — source balance before/after the operation.
- `BigDecimal getTargetBefore()` / `getTargetAfter()` — target balance before/after the operation.

Behavior and Guidance:
- Use `isSuccess()` to decide if follow-up work (e.g., notifications, external hooks) should run.
- Because both pre- and post-events run on the main thread, keep listeners fast and non-blocking.

Example usage (listener):
```
@EventHandler
public void onPostTx(PostTransactionEvent e) {
    if (e.isSuccess() && e.getType() == TransactionType.PAY) {
        // increment metric or notify external system
    }
}
```
