---
title: Money Formatting
nav_order: 4
parent: Features
---

# Money Formatting

This page summarizes recommended options for formatting money amounts in EzEconomy: numeric precision, separators, currency symbols, localization, rounding, compact formats (K/M/B), and Minecraft-specific color codes.

## Overview

- **Presentation vs Storage:** Store amounts in a precise internal type (prefer `long` cents or `BigDecimal`) and format at presentation time. This prevents rounding surprises and keeps arithmetic precise.
- **Two concerns:** 1) how values are stored and computed, 2) how values are displayed to players and integrations.

## Numeric types and precision

- **long (cents):** Store integer cents (e.g., 12345 = $123.45). Fast and safe for atomic operations.
- **BigDecimal:** Use for complex financial logic where fractional cents and arbitrary precision are required. Always specify scale and `RoundingMode` where necessary.

Recommendation: Use `long` for typical Minecraft economy (scaled by 100) and `BigDecimal` if you need higher precision or currency conversions.

## Formatting options

- **Pattern-based formatting:** Use `DecimalFormat` / `NumberFormat` patterns, e.g. `#,##0.00`.
- **Locale-aware:** `NumberFormat.getCurrencyInstance(locale)` will use the locale's separators and currency symbols.
- **Symbol placement:** `prefix` (e.g., `$1,234.56`) or `suffix` (e.g., `1.234,56€`). Make this configurable.
- **Grouping separators:** Use thousands separators (`,`) or space / none depending on locale.
- **Decimal places:** Typically 2 decimal places for cents; configurable `scale` for other currency models.
- **Rounding:** Support `HALF_UP`, `HALF_EVEN`, `DOWN`, etc. Rounding should be explicit in docs and configuration.
- **Negative formatting:** `-1,234.56` or `(1,234.56)` — allow config choice.
- **Show trailing zeros:** Option to display `1.50` vs `1.5`.

## Compact / Short formats

- **Human-readable suffixes:** Convert large numbers to `1.2K`, `3.4M`, `2B` for UI compactness. Provide configurable thresholds and suffixes.
- **Precision for short formats:** Usually 1 decimal place (e.g., `1.2K`). Configurable.

## Minecraft-specific presentation

- **Color codes:** Support legacy ampersand `&` formatting for older versions and hex (`&#RRGGBB`) for newer ones. Allow separate color for positive/negative values.
- **Placeholders & GUI:** Provide both full and compact placeholders (e.g., `%ezeconomy_balance_formatted%` and `%ezeconomy_balance_short%`).

## Config examples

Example YAML structure to add under `config.yml` (presentational options only):

```yaml
money-format:
  pattern: "#,##0.00"          # DecimalFormat pattern
  locale: "en_US"             # Locale used for separators and currency symbol
  currencySymbol: "$"         # Symbol to use (overrides locale symbol if provided)
  symbolPlacement: "prefix"   # prefix|suffix
  showTrailingZeros: true       # always show two decimals
  roundingMode: "HALF_UP"     # HALF_UP, HALF_EVEN, DOWN, etc.
  negativeFormat: "-amount"   # "-amount" or "(amount)"
  useCompact: false             # show 1.2K style in some UIs
  compact:
    thresholds:
      thousand: 1000
      million: 1000000
    suffixes:
      thousand: "K"
      million: "M"
    precision: 1
  colors:
    useColors: true
    positive: "&a"             # legacy color code prefix
    negative: "&c"
```

Notes:

- If `locale` is set, server owners should ensure it matches their desired separators and symbol conventions.
- `currencySymbol` can be empty to rely on locale defaults or filled for multi-currency servers.

## Java usage examples

DecimalFormat example (apply pattern, locale and rounding):

```java
Locale locale = Locale.forLanguageTag("en-US");
DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance(locale);
df.applyPattern("#,##0.00");
df.setRoundingMode(RoundingMode.HALF_UP);
String out = df.format(amountInCents / 100.0);
// apply symbol placement and color wrapping as configured
```

If storing as `long` cents:

```java
long cents = 12345L;
double display = cents / 100.0;
String formatted = df.format(display);
```

For `BigDecimal`:

```java
BigDecimal amount = new BigDecimal("123.45");
amount = amount.setScale(2, RoundingMode.HALF_UP);
String out = df.format(amount);
```

## PlaceholderAPI / plugin placeholders

- Provide placeholders for common needs:
  - `%ezeconomy_balance%` — raw numeric value
  - `%ezeconomy_balance_formatted%` — formatted per `money-format` settings
  - `%ezeconomy_balance_short%` — compact K/M form

Document the difference and recommended use cases in GUI vs chat vs logs.

## Recommendations & best practices

- **Separate concerns:** Keep storage and formatting code separate (service layer formats for presentation only).
- **Make presentation configurable:** Many regions prefer different separators, symbol placement, or compact notation.
- **Avoid floating-point for storage:** Use `long` cents or `BigDecimal` to prevent precision loss.
- **Explicit rounding:** Always document the rounding mode used for displayed and persisted values.
- **Provide both full and compact placeholders:** Give server owners flexibility for GUIs, chat, and action bar displays.

## Next steps for integrators

- Add `money-format` section to `config.yml` and expose it in admin UI.
- Wire placeholders to use the configured formatter.
- Provide a small test page or command (e.g., `/moneyformat test`) to show how settings affect output.
