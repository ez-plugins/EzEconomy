package com.skyblockexp.ezeconomy.feature.support;

import org.mockbukkit.mockbukkit.MockBukkit;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;

import java.lang.reflect.Field;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.api.storage.models.Transaction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test utilities shared between feature tests.
 */

public final class TestSupport {
    private TestSupport() {}

    public static Object setupMockServer() {
        try {
            return MockBukkit.mock();
        } catch (IllegalStateException e) {
            try { MockBukkit.unmock(); } catch (Exception ignored) {}
            return MockBukkit.mock();
        }
    }

    public static EzEconomyPlugin loadPlugin(Object server) {
        EzEconomyPlugin plugin = (EzEconomyPlugin) MockBukkit.load(EzEconomyPlugin.class);
        try {
            plugin.registerEconomy();
        } catch (Exception ignored) {}
        return plugin;
    }

    public static void tearDown() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {}
    }

    public static void injectField(Object target, String fieldName, Object value) {
        try {
            Class<?> cls = target.getClass();
            while (cls != null) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Lightweight in-memory StorageProvider for feature tests.
     */
    public static class MockStorage implements StorageProvider {
        private final Map<String, Double> balances = new HashMap<>();
        // bankName -> (currency -> balance)
        private final Map<String, java.util.Map<String, Double>> bankBalances = new HashMap<>();
        // bankName -> owner UUID
        private final Map<String, UUID> bankOwners = new HashMap<>();
        // bankName -> members
        private final Map<String, java.util.Set<UUID>> bankMembers = new HashMap<>();

        private String key(UUID uuid, String currency) {
            return uuid.toString() + ":" + currency;
        }

        @Override
        public void init() {}

        @Override
        public void load() {}

        @Override
        public void save() {}

        @Override
        public double getBalance(UUID uuid, String currency) {
            return balances.getOrDefault(key(uuid, currency), 0.0);
        }

        @Override
        public void setBalance(UUID uuid, String currency, double amount) {
            balances.put(key(uuid, currency), amount);
        }

        @Override
        public void logTransaction(Transaction transaction) {}

        @Override
        public java.util.List<Transaction> getTransactions(UUID uuid, String currency) { return java.util.Collections.emptyList(); }

        @Override
        public boolean tryWithdraw(UUID uuid, String currency, double amount) {
            double bal = getBalance(uuid, currency);
            if (bal < amount) return false;
            setBalance(uuid, currency, bal - amount);
            return true;
        }

        @Override
        public void deposit(UUID uuid, String currency, double amount) {
            double bal = getBalance(uuid, currency);
            setBalance(uuid, currency, bal + amount);
        }

        @Override
        public java.util.Map<UUID, Double> getAllBalances(String currency) {
            java.util.Map<UUID, Double> out = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Double> e : balances.entrySet()) {
                String k = e.getKey();
                int idx = k.lastIndexOf(":");
                if (idx <= 0) continue;
                String uuidStr = k.substring(0, idx);
                String cur = k.substring(idx + 1);
                if (!currency.equals(cur)) continue;
                try {
                    UUID id = UUID.fromString(uuidStr);
                    out.put(id, e.getValue());
                } catch (IllegalArgumentException ignore) {}
            }
            return out;
        }

        @Override
        public boolean playerExists(UUID uuid) {
            String prefix = uuid.toString() + ":";
            for (String k : balances.keySet()) {
                if (k.startsWith(prefix)) return true;
            }
            return false;
        }

        @Override
        public void shutdown() {}

        // Bank methods - trivial implementations for tests
        @Override public boolean createBank(String name, UUID owner) {
            synchronized (this) {
                if (bankBalances.containsKey(name)) return false;
                bankBalances.put(name, new java.util.HashMap<>());
                java.util.Map<String, Double> map = bankBalances.get(name);
                map.put("dollar", 0.0);
                bankOwners.put(name, owner);
                bankMembers.put(name, new java.util.HashSet<>());
                return true;
            }
        }

        @Override public boolean deleteBank(String name) {
            synchronized (this) {
                boolean ex = bankBalances.containsKey(name);
                bankBalances.remove(name);
                bankOwners.remove(name);
                bankMembers.remove(name);
                return ex;
            }
        }

        @Override public boolean bankExists(String name) {
            return bankBalances.containsKey(name);
        }

        @Override public double getBankBalance(String name, String currency) {
            java.util.Map<String, Double> map = bankBalances.get(name);
            if (map == null) return 0.0;
            return map.getOrDefault(currency, 0.0);
        }

        @Override public void setBankBalance(String name, String currency, double amount) {
            bankBalances.computeIfAbsent(name, k -> new java.util.HashMap<>()).put(currency, amount);
        }

        @Override public boolean tryWithdrawBank(String name, String currency, double amount) {
            synchronized (this) {
                double bal = getBankBalance(name, currency);
                if (bal < amount) return false;
                setBankBalance(name, currency, bal - amount);
                return true;
            }
        }

        @Override public void depositBank(String name, String currency, double amount) {
            synchronized (this) {
                double bal = getBankBalance(name, currency);
                setBankBalance(name, currency, bal + amount);
            }
        }

        @Override public java.util.Set<String> getBanks() { return new java.util.HashSet<>(bankBalances.keySet()); }

        @Override public boolean isBankOwner(String name, UUID uuid) {
            UUID o = bankOwners.get(name);
            return o != null && o.equals(uuid);
        }

        @Override public boolean isBankMember(String name, UUID uuid) {
            java.util.Set<UUID> m = bankMembers.get(name);
            return m != null && m.contains(uuid);
        }

        @Override public boolean addBankMember(String name, UUID uuid) {
            bankMembers.computeIfAbsent(name, k -> new java.util.HashSet<>()).add(uuid);
            return true;
        }

        @Override public boolean removeBankMember(String name, UUID uuid) {
            java.util.Set<UUID> m = bankMembers.get(name);
            if (m == null) return false;
            return m.remove(uuid);
        }

        @Override public java.util.Set<UUID> getBankMembers(String name) {
            java.util.Set<UUID> m = bankMembers.get(name);
            return m == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(m);
        }

        @Override
        public com.skyblockexp.ezeconomy.dto.EconomyPlayer getPlayer(UUID uuid) {
            org.bukkit.OfflinePlayer of = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = of != null && of.getName() != null ? of.getName() : uuid.toString();
            String display = (of instanceof org.bukkit.entity.Player) ? ((org.bukkit.entity.Player) of).getDisplayName() : name;
            return new com.skyblockexp.ezeconomy.dto.EconomyPlayer(uuid, name, display);
        }
    }

    public static java.io.File createTestDataFolder(EzEconomyPlugin plugin, String folderName) {
        java.io.File df = new java.io.File(plugin.getDataFolder(), folderName);
        if (!df.exists()) df.mkdirs();
        return df;
    }

    public static void cleanupTestDataFolder(EzEconomyPlugin plugin, String folderName) {
        java.io.File df = new java.io.File(plugin.getDataFolder(), folderName);
        if (df.exists()) {
            java.io.File[] files = df.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    try { f.delete(); } catch (Exception ignored) {}
                }
            }
            try { df.delete(); } catch (Exception ignored) {}
        }
    }

    public static Object getField(Object target, String fieldName) {
        try {
            Class<?> cls = target.getClass();
            while (cls != null) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
