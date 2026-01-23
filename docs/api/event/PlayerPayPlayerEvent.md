# PlayerPayPlayerEvent

Class: `com.skyblockexp.ezeconomy.api.events.PlayerPayPlayerEvent`

Overview:
- Specialized event that extends `PreTransactionEvent` for player-to-player payments.
- Fired by the `/pay` command before the transfer is executed.
- Cancellable; cancelling prevents the payment and causes the command to report cancellation to the payer.

Fields / Accessors:
- Inherits all fields from `PreTransactionEvent` (`getSource`, `getTarget`, `getAmount`, `getType`, cancellation helpers).

Behavior and Guidance:
- Because this extends `PreTransactionEvent` its cancellation semantics and `cancelReason` apply directly to payer-facing feedback.
- Listeners can examine the payer (`getSource()`) and payee (`getTarget()`) and block or modify behavior.
- Note: listeners should not perform long blocking work; if async work is required, schedule it and cancel/allow based on synchronous checks only.

Example usage (listener):
```
@EventHandler
public void onPlayerPay(PlayerPayPlayerEvent e) {
    // block payments to a specific UUID
    if (e.getTarget() != null && e.getTarget().equals(UUID.fromString("..."))) {
        e.setCancelled(true);
        e.setCancelReason("&cThis player does not accept payments.");
    }
}
```
