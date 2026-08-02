package com.skyblockexp.ezeconomy.util;

import com.skyblockexp.ezeconomy.core.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class NumberUtil {
    /**
     * Parse user-provided amount strings into a Money object for a given currency id.
     * Accepts optional commas, leading/trailing currency symbols, and suffixes (k,m,b,t,qa,qi,sx,sp,oc,no,dc).
     * Examples: "$1,234.56", "1k", "2.5m", "€3,000"
     * Returns null when parsing fails.
     */
    public static Money parseMoney(String input, String currencyId) {
        if (input == null || input.isEmpty()) return null;
        String raw = input.trim();

        // Remove common currency symbols and whitespace
        raw = raw.replace('\u00A0', ' ');
        raw = raw.replaceAll("[\\$€£¥]", "").trim();

        // Lowercase for suffix handling
        String lower = raw.toLowerCase(Locale.ROOT);
        try {
            BigDecimal multiplier = BigDecimal.ONE;
            if (lower.endsWith("k")) {
                multiplier = BigDecimal.valueOf(1_000L);
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("m")) {
                multiplier = BigDecimal.valueOf(1_000_000L);
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("b")) {
                multiplier = BigDecimal.valueOf(1_000_000_000L);
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("t")) {
                multiplier = BigDecimal.valueOf(1_000_000_000_000L);
                lower = lower.substring(0, lower.length() - 1);
            } else if (lower.endsWith("qa")) {
                multiplier = BigDecimal.valueOf(1_000_000_000_000_000L);
                lower = lower.substring(0, lower.length() - 2);
            } else if (lower.endsWith("qi")) {
                multiplier = new BigDecimal("1000000000000000000");
                lower = lower.substring(0, lower.length() - 2);
            } else if (lower.endsWith("sx")) {
                multiplier = new BigDecimal("1000000000000000000000");
                lower = lower.substring(0, lower.length() - 2);
            } else if (lower.endsWith("sp")) {
                multiplier = new BigDecimal("1000000000000000000000000");
                lower = lower.substring(0, lower.length() - 2);
            } else if (lower.endsWith("oc")) {
                multiplier = new BigDecimal("1000000000000000000000000000");
                lower = lower.substring(0, lower.length() - 2);
            } else if (lower.endsWith("no")) {
                multiplier = new BigDecimal("1000000000000000000000000000000");
                lower = lower.substring(0, lower.length() - 2);
            } else if (lower.endsWith("dc")) {
                multiplier = new BigDecimal("1000000000000000000000000000000000");
                lower = lower.substring(0, lower.length() - 2);

            // Normalize spacing and non-digit separators
            String candidate = lower.replaceAll("\\s+", "");

            // Heuristic to detect German-style numbers (e.g. "1.234,56") vs US ("1,234.56")
            boolean looksGerman = false;
            if (candidate.contains(",") && candidate.contains(".")) {
                // If the last comma appears after the last dot, treat as German decimal separator
                looksGerman = candidate.lastIndexOf(',') > candidate.lastIndexOf('.');
            } else if (candidate.contains(",") && !candidate.contains(".")) {
                // If there's a comma and no dot, it might be either thousands (US) or decimal (German).
                // If there are exactly three digits after the comma, it's likely grouping (treat as US);
                // otherwise treat comma as decimal (German).
                int idx = candidate.lastIndexOf(',');
                if (candidate.length() - idx - 1 != 3) {
                    looksGerman = true;
                }
            }

            Number parsed;
            if (looksGerman) {
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
                nf.setGroupingUsed(true);
                parsed = nf.parse(candidate);
            } else {
                // Default to US-style parsing (commas as grouping, dot as decimal)
                String cleaned = candidate.replaceAll(",", "");
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
                nf.setGroupingUsed(true);
                parsed = nf.parse(cleaned);
            }

            BigDecimal value = new BigDecimal(parsed.toString()).multiply(multiplier);
            return Money.of(value.setScale(10, RoundingMode.HALF_UP), currencyId);
        } catch (ParseException | NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Return a short human-friendly representation using k/m/b/t suffixes.
     * Examples: 1500 -> "1.5k", 1000 -> "1k", 2500000 -> "2.5m"
     */
    public static String formatShort(java.math.BigDecimal value) {
        return formatShort(value, 1);
    }

    /**
     * Format short with configurable decimal precision for the suffix value.
     * Example: decimals=2 -> 1500 -> "1.50k"
     */
    public static String formatShort(java.math.BigDecimal value, int decimals) {
        if (value == null) return "0";
        java.math.BigDecimal abs = value.abs();
        java.math.BigDecimal thousand = new java.math.BigDecimal("1000");
        java.math.BigDecimal million = new java.math.BigDecimal("1000000");
        java.math.BigDecimal billion = new java.math.BigDecimal("1000000000");
        java.math.BigDecimal trillion = new java.math.BigDecimal("1000000000000");
        java.math.BigDecimal quadrillion = new java.math.BigDecimal("1000000000000000");
        java.math.BigDecimal quintillion = new java.math.BigDecimal("1000000000000000000");
        java.math.BigDecimal sextillion = new java.math.BigDecimal("1000000000000000000000");
        java.math.BigDecimal septillion = new java.math.BigDecimal("1000000000000000000000000");
        java.math.BigDecimal octillion = new java.math.BigDecimal("1000000000000000000000000000");
        java.math.BigDecimal nonillion = new java.math.BigDecimal("1000000000000000000000000000000");
        java.math.BigDecimal decillion = new java.math.BigDecimal("1000000000000000000000000000000000");

        String suffix = "";
        java.math.BigDecimal display;
        if (abs.compareTo(decillion) >= 0) {
            display = value.divide(decillion, decimals, RoundingMode.HALF_UP);
            suffix = "dc";
        } else if (abs.compareTo(nonillion) >= 0) {
            display = value.divide(nonillion, decimals, RoundingMode.HALF_UP);
            suffix = "no";
        } else if (abs.compareTo(octillion) >= 0) {
            display = value.divide(octillion, decimals, RoundingMode.HALF_UP);
            suffix = "oc";
        } else if (abs.compareTo(septillion) >= 0) {
            display = value.divide(septillion, decimals, RoundingMode.HALF_UP);
            suffix = "sp";
        } else if (abs.compareTo(sextillion) >= 0) {
            display = value.divide(sextillion, decimals, RoundingMode.HALF_UP);
            suffix = "sx";
        } else if (abs.compareTo(quintillion) >= 0) {
            display = value.divide(quintillion, decimals, RoundingMode.HALF_UP);
            suffix = "qi";
        } else if (abs.compareTo(quadrillion) >= 0) {
            display = value.divide(quadrillion, decimals, RoundingMode.HALF_UP);
            suffix = "qa";
        } else if (abs.compareTo(trillion) >= 0) {
            display = value.divide(trillion, decimals, RoundingMode.HALF_UP);
            suffix = "t";
        } else if (abs.compareTo(billion) >= 0) {
            display = value.divide(billion, decimals, RoundingMode.HALF_UP);
            suffix = "b";
        } else if (abs.compareTo(million) >= 0) {
            display = value.divide(million, decimals, RoundingMode.HALF_UP);
            suffix = "m";
        } else if (abs.compareTo(thousand) >= 0) {
            display = value.divide(thousand, decimals, RoundingMode.HALF_UP);
            suffix = "k";
        } else {
            // small values: return plain integer/decimal string without suffix
            java.math.BigDecimal stripped = value.stripTrailingZeros();
            return stripped.scale() <= 0 ? stripped.toPlainString() : stripped.toPlainString();
        }
        // strip trailing zeros beyond decimal point (e.g. 1.00 -> 1)
        java.math.BigDecimal stripped = display.stripTrailingZeros();
        return stripped.toPlainString() + suffix;
    }

    /**
     * Backwards-compatible API: parse to a primitive double using suffix rules.
     * Returns Double.NaN on failure.
     */
    public static double parseAmount(String input) {
        Money m = parseMoney(input, null);
        if (m == null) return Double.NaN;
        try {
            return m.getAmount().doubleValue();
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /**
     * Compatibility alias for parseAmount(String).
     */
    public static Double parseDouble(String input) {
        return parseAmount(input);
    }
}
