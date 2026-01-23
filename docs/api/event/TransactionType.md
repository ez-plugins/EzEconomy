# TransactionType

Enum: `com.skyblockexp.ezeconomy.api.events.TransactionType`

Values:
- `DEPOSIT` — money added to a single account.
- `WITHDRAW` — money removed from a single account.
- `TRANSFER` — generic transfer between two accounts (internal use by storage `transfer`).
- `PAY` — player-to-player payment (used by `PlayerPayPlayerEvent`).
- `BANK_DEPOSIT` — deposit into a bank account.
- `BANK_WITHDRAW` — withdrawal from a bank account.

Purpose: classify the kind of economy operation when firing pre/post transaction events so listeners can filter by `TransactionType`.
