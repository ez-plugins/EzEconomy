package com.skyblockexp.ezeconomy.api.storage.models;

import java.util.UUID;

public class Transaction {
    private UUID uuid;
    private String currency;
    private double amount;
    private long timestamp;

    public Transaction(UUID uuid, String currency, double amount, long timestamp) {
        this.uuid = uuid;
        this.currency = currency;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public UUID getUuid() { return uuid; }
    public String getCurrency() { return currency; }
    public double getAmount() { return amount; }
    public long getTimestamp() { return timestamp; }
}
