package com.skyblockexp.ezeconomy.dto;

/**
 * Data Transfer Object for currency data.
 */
public class CurrencyDTO {
    private final String code;
    private final String displayName;

    public CurrencyDTO(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
}
