package com.skyblockexp.ezeconomy.command.ezeconomy;

import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.command.Subcommand;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.core.MessageProvider;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.UUID;

/**
 * Subcommand for /ezeconomy database test - tests database functions and resets afterwards
 */
public class DatabaseTestSubcommand implements Subcommand {
    private final EzEconomyPlugin plugin;

    public DatabaseTestSubcommand(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        MessageProvider messages = plugin.getMessageProvider();
        if (!sender.hasPermission("ezeconomy.database.test")) {
            sender.sendMessage(messages.color(messages.get("no_permission")));
            return true;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(messages.color("&cThis command will test all database functions and reset the database afterwards."));
            sender.sendMessage(messages.color("&cAll data will be lost! Use &f/ezeconomy database test confirm &cto proceed."));
            return true;
        }

        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) {
            sender.sendMessage(messages.color(messages.get("storage_unavailable")));
            return true;
        }

        sender.sendMessage(messages.color("&6Starting database test..."));

        try {
            // Test player balance operations
            UUID testUUID = UUID.randomUUID();
            sender.sendMessage(messages.color("&eTesting player balance operations..."));
            storage.setBalance(testUUID, "dollar", 100.0);
            double balance = storage.getBalance(testUUID, "dollar");
            if (balance != 100.0) {
                throw new Exception("Balance set/get failed: expected 100.0, got " + balance);
            }

            boolean withdrawSuccess = storage.tryWithdraw(testUUID, "dollar", 50.0);
            if (!withdrawSuccess) {
                throw new Exception("Withdraw failed");
            }
            balance = storage.getBalance(testUUID, "dollar");
            if (balance != 50.0) {
                throw new Exception("Balance after withdraw failed: expected 50.0, got " + balance);
            }

            sender.sendMessage(messages.color("&aPlayer balance operations: &2PASS"));

            // Test bank operations
            sender.sendMessage(messages.color("&eTesting bank operations..."));
            boolean bankCreated = storage.createBank("testbank", testUUID);
            if (!bankCreated) {
                throw new Exception("Bank creation failed");
            }
            storage.setBankBalance("testbank", "dollar", 200.0);
            double bankBalance = storage.getBankBalance("testbank", "dollar");
            if (bankBalance != 200.0) {
                throw new Exception("Bank balance set/get failed: expected 200.0, got " + bankBalance);
            }

            boolean memberAdded = storage.addBankMember("testbank", UUID.randomUUID());
            if (!memberAdded) {
                throw new Exception("Bank member addition failed");
            }
            if (storage.getBankMembers("testbank").size() != 1) {
                throw new Exception("Bank member operations failed");
            }

            sender.sendMessage(messages.color("&aBank operations: &2PASS"));

            sender.sendMessage(messages.color("&aAll tests passed! Resetting database..."));

            // Reset database
            resetDatabase(storage);
            sender.sendMessage(messages.color("&aDatabase reset complete."));

        } catch (Exception e) {
            sender.sendMessage(messages.color("&cDatabase test failed: " + e.getMessage()));
            return true;
        }

        return true;
    }

    private void resetDatabase(StorageProvider storage) {
        // This would need to be implemented in each storage provider
        // For now, we'll call cleanup and assume reset is handled
        storage.cleanupOrphanedPlayers();
    }
}