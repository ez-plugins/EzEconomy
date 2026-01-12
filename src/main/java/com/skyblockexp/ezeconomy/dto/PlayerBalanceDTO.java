package com.skyblockexp.ezeconomy.dto;

import java.util.UUID;

/**
 * Data Transfer Object for player balance.
 */
public class PlayerBalanceDTO {
    private final UUID uuid;
    private final String currency;
    private final double balance;

    public PlayerBalanceDTO(UUID uuid, String currency, double balance) {
        this.uuid = uuid;
        this.currency = currency;
        this.balance = balance;
    }

    public UUID getUuid() { return uuid; }
    public String getCurrency() { return currency; }
    public double getBalance() { return balance; }
}
