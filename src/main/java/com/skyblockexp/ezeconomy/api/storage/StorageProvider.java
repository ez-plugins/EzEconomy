package com.skyblockexp.ezeconomy.api.storage;

import java.util.UUID;

public interface StorageProvider {
    double getBalance(UUID uuid, String currency);
    void setBalance(UUID uuid, String currency, double amount);
    // Add other methods as needed for your plugin
}
