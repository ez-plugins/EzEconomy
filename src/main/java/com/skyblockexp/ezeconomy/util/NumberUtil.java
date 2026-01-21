package com.skyblockexp.ezeconomy.util;

public class NumberUtil {
    /**
     * Parses a string with k/m/b suffixes to a double value.
     * Examples: 1k = 1000, 2.5m = 2_500_000, 3b = 3_000_000_000
     * Returns NaN if invalid.
     */
    public static double parseAmount(String input) {
        if (input == null || input.isEmpty()) return Double.NaN;
        input = input.trim().toLowerCase();
        try {
            if (input.endsWith("k")) {
                return Double.parseDouble(input.substring(0, input.length() - 1)) * 1_000d;
            } else if (input.endsWith("m")) {
                return Double.parseDouble(input.substring(0, input.length() - 1)) * 1_000_000d;
            } else if (input.endsWith("b")) {
                return Double.parseDouble(input.substring(0, input.length() - 1)) * 1_000_000_000d;
            } else if (input.endsWith("t")) {
                return Double.parseDouble(input.substring(0, input.length() - 1)) * 1_000_000_000_000d;
            } else {
                return Double.parseDouble(input);
            }
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
    /**
     * Compatibility alias for parseAmount(String).
     * Provided for legacy code expecting parseDouble.
     */
    public static Double parseDouble(String input) {
        return parseAmount(input);
    }
}
