# PreTransactionEvent

Class: `com.skyblockexp.ezeconomy.api.events.PreTransactionEvent`

Overview:
- Fired before an economy mutation is applied (transfer, deposit, withdraw).
- This event is *cancellable*; cancelling it should abort the pending operation.
- Fired synchronously on the server main thread (storage implementations ensure sync dispatch).

Fields / Accessors:
- `UUID getSource()` — the account initiating the transaction (may be `null` for system operations).
- `UUID getTarget()` — the target account (may be `null` for single-account ops).
- `BigDecimal getAmount()` — the requested amount.
- `TransactionType getType()` — the kind of operation (see `TransactionType`).
- `boolean isCancelled()` / `void setCancelled(boolean)` — cancellation state.
- `String getCancelReason()` / `void setCancelReason(String)` — optional reason to display to callers.

Behavior and Guidance:
- If a listener cancels the event, storage methods and higher-level callers treat the operation as failed.
- Listeners should set `cancelReason` when wanting a human-readable message returned to the caller.
- Because the event runs on the main thread, avoid long/blocking work in listeners.

Example usage (listener):
```
@EventHandler
public void onPreTx(PreTransactionEvent e) {
    if (e.getType() == TransactionType.PAY && e.getAmount().compareTo(new BigDecimal("1000")) > 0) {
        e.setCancelled(true);
        e.setCancelReason("&cPayments over 1000 are blocked by policy.");
    }
}
```
