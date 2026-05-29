package com.skyblockexp.ezeconomy.benchmark;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class EconomyBenchmarkHarnessPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getScheduler().runTaskLater(this, this::runBenchmarks, 40L);
    }

    private void runBenchmarks() {
        long runStartNanos = System.nanoTime();
        String pluginUnderTest = envOrDefault("BENCH_PLUGIN", "unknown");
        String pluginVersion = envOrDefault("BENCH_PLUGIN_VERSION", "unknown");
        String storageMode = envOrDefault("BENCH_STORAGE", "unknown");
        String redisMode = envOrDefault("BENCH_REDIS", "unknown");
        int warmupIterations = intEnv("BENCH_WARMUP", 200);
        int measureIterations = intEnv("BENCH_ITERATIONS", 2000);
        String runId = envOrDefault("GITHUB_RUN_ID", "local");
        String runAttempt = envOrDefault("GITHUB_RUN_ATTEMPT", "1");

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            writeFailure(pluginUnderTest, pluginVersion, storageMode, redisMode, warmupIterations, measureIterations,
                "Vault economy provider not found");
            shutdown();
            return;
        }

        Economy economy = rsp.getProvider();
        String activeProvider = economy.getName();

        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        String world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();

        try {
            resetPlayer(economy, player, world);

            BenchResult deposit = benchDeposit(economy, player, world, warmupIterations, measureIterations);
            BenchResult withdraw = benchWithdraw(economy, player, world, warmupIterations, measureIterations);
            BenchResult balance = benchBalanceAndHas(economy, player, world, warmupIterations, measureIterations);
            long runDurationMs = (System.nanoTime() - runStartNanos) / 1_000_000L;

            writeOutputs(pluginUnderTest, pluginVersion, storageMode, redisMode, warmupIterations, measureIterations,
                runId, runAttempt, activeProvider, deposit, withdraw, balance, runDurationMs);
        } catch (Exception ex) {
            writeFailure(pluginUnderTest, pluginVersion, storageMode, redisMode, warmupIterations, measureIterations,
                "Benchmark failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            getLogger().warning("Benchmark failed");
            ex.printStackTrace();
        }

        runBankBenchmarks(economy, activeProvider, pluginUnderTest, pluginVersion, storageMode, redisMode,
            warmupIterations, measureIterations, runId, runAttempt);
        getLogger().info("All benchmark results written");
        shutdown();
    }

    private static BenchResult benchDeposit(Economy economy, OfflinePlayer player, String world, int warmup, int iterations) {
        for (int i = 0; i < warmup; i++) {
            deposit(economy, player, world, 1.0D);
        }
        long totalNanos = 0L;
        long[] samples = new long[iterations];
        RamStats ramStats = new RamStats();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            deposit(economy, player, world, 1.0D);
            long elapsed = System.nanoTime() - start;
            samples[i] = elapsed;
            totalNanos += elapsed;
            ramStats.capture();
        }
        return BenchResult.from("deposit", totalNanos, samples, ramStats);
    }

    private static BenchResult benchWithdraw(Economy economy, OfflinePlayer player, String world, int warmup, int iterations) {
        deposit(economy, player, world, warmup + iterations + 100.0D);
        for (int i = 0; i < warmup; i++) {
            withdraw(economy, player, world, 1.0D);
        }
        long totalNanos = 0L;
        long[] samples = new long[iterations];
        RamStats ramStats = new RamStats();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            withdraw(economy, player, world, 1.0D);
            long elapsed = System.nanoTime() - start;
            samples[i] = elapsed;
            totalNanos += elapsed;
            ramStats.capture();
        }
        return BenchResult.from("withdraw", totalNanos, samples, ramStats);
    }

    private static BenchResult benchBalanceAndHas(Economy economy, OfflinePlayer player, String world, int warmup, int iterations) {
        deposit(economy, player, world, 1000.0D);
        for (int i = 0; i < warmup; i++) {
            getBalance(economy, player, world);
            has(economy, player, world, 1.0D);
        }
        long totalNanos = 0L;
        long[] samples = new long[iterations];
        RamStats ramStats = new RamStats();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            getBalance(economy, player, world);
            has(economy, player, world, 1.0D);
            long elapsed = System.nanoTime() - start;
            samples[i] = elapsed;
            totalNanos += elapsed;
            ramStats.capture();
        }
        return BenchResult.from("balance_has", totalNanos, samples, ramStats);
    }

    private static void resetPlayer(Economy economy, OfflinePlayer player, String world) {
        double current = getBalance(economy, player, world);
        if (current > 0.0D) {
            withdraw(economy, player, world, current);
        }
    }

    private static void deposit(Economy economy, OfflinePlayer player, String world, double amount) {
        if (world != null && economy.hasBankSupport()) {
            // noop: keep world lookup branch explicit for provider compatibility checks
        }
        if (world == null) {
            economy.depositPlayer(player, amount);
            return;
        }
        try {
            economy.depositPlayer(player, world, amount);
        } catch (Throwable ignored) {
            economy.depositPlayer(player, amount);
        }
    }

    private static void withdraw(Economy economy, OfflinePlayer player, String world, double amount) {
        if (world == null) {
            economy.withdrawPlayer(player, amount);
            return;
        }
        try {
            economy.withdrawPlayer(player, world, amount);
        } catch (Throwable ignored) {
            economy.withdrawPlayer(player, amount);
        }
    }

    private static double getBalance(Economy economy, OfflinePlayer player, String world) {
        if (world == null) {
            return economy.getBalance(player);
        }
        try {
            return economy.getBalance(player, world);
        } catch (Throwable ignored) {
            return economy.getBalance(player);
        }
    }

    private static boolean has(Economy economy, OfflinePlayer player, String world, double amount) {
        if (world == null) {
            return economy.has(player, amount);
        }
        try {
            return economy.has(player, world, amount);
        } catch (Throwable ignored) {
            return economy.has(player, amount);
        }
    }

    private void writeOutputs(String plugin, String pluginVersion, String storage, String redis,
                              int warmup, int iterations, String runId, String runAttempt, String provider,
                              BenchResult deposit, BenchResult withdraw, BenchResult balanceHas, long runtimeMs) {
        File outDir = new File(getDataFolder(), "results");
        if (!outDir.exists() && !outDir.mkdirs()) {
            getLogger().warning("Failed to create results directory: " + outDir.getAbsolutePath());
            return;
        }

        File jsonFile = new File(outDir, "result.json");
        File csvFile = new File(outDir, "result.csv");

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());

        String json = "{\n"
            + "  \"status\": \"ok\",\n"
            + "  \"plugin\": \"" + escape(plugin) + "\",\n"
            + "  \"pluginVersion\": \"" + escape(pluginVersion) + "\",\n"
            + "  \"activeVaultProvider\": \"" + escape(provider) + "\",\n"
            + "  \"storage\": \"" + escape(storage) + "\",\n"
            + "  \"redis\": \"" + escape(redis) + "\",\n"
            + "  \"warmupIterations\": " + warmup + ",\n"
            + "  \"measureIterations\": " + iterations + ",\n"
            + "  \"runtimeMs\": " + runtimeMs + ",\n"
            + "  \"runId\": \"" + escape(runId) + "\",\n"
            + "  \"runAttempt\": \"" + escape(runAttempt) + "\",\n"
            + "  \"timestamp\": \"" + escape(now) + "\",\n"
            + "  \"metrics\": {\n"
            + metricJson("deposit", deposit) + ",\n"
            + metricJson("withdraw", withdraw) + ",\n"
            + metricJson("balance_has", balanceHas) + "\n"
            + "  }\n"
            + "}\n";

        writeText(jsonFile, json);

        String csv = "plugin,plugin_version,active_provider,storage,redis,operation,average_ns,average_ms,p95_ns,p95_ms,avg_used_ram_bytes,avg_used_ram_mib,peak_used_ram_bytes,peak_used_ram_mib,iterations,warmup,timestamp\n"
            + csvLine(plugin, pluginVersion, provider, storage, redis, deposit, iterations, warmup, now)
            + csvLine(plugin, pluginVersion, provider, storage, redis, withdraw, iterations, warmup, now)
            + csvLine(plugin, pluginVersion, provider, storage, redis, balanceHas, iterations, warmup, now);

        writeText(csvFile, csv);
        getLogger().info("Benchmark results written to " + outDir.getAbsolutePath());
    }

    private static String metricJson(String key, BenchResult result) {
        return "    \"" + key + "\": {"
            + "\"averageNs\": " + result.averageNs + ", "
            + "\"averageMs\": " + formatDouble(result.averageMs) + ", "
            + "\"p95Ns\": " + result.p95Ns + ", "
            + "\"p95Ms\": " + formatDouble(result.p95Ms) + ", "
            + "\"avgUsedRamBytes\": " + result.avgUsedRamBytes + ", "
            + "\"avgUsedRamMiB\": " + formatDouble(result.avgUsedRamMiB) + ", "
            + "\"peakUsedRamBytes\": " + result.peakUsedRamBytes + ", "
            + "\"peakUsedRamMiB\": " + formatDouble(result.peakUsedRamMiB) + "}";
    }

    private static String csvLine(String plugin, String pluginVersion, String provider, String storage, String redis,
                                  BenchResult result, int iterations, int warmup, String timestamp) {
        return quoteCsv(plugin) + ","
            + quoteCsv(pluginVersion) + ","
            + quoteCsv(provider) + ","
            + quoteCsv(storage) + ","
            + quoteCsv(redis) + ","
            + quoteCsv(result.operation) + ","
            + result.averageNs + ","
            + formatDouble(result.averageMs) + ","
            + result.p95Ns + ","
            + formatDouble(result.p95Ms) + ","
            + result.avgUsedRamBytes + ","
            + formatDouble(result.avgUsedRamMiB) + ","
            + result.peakUsedRamBytes + ","
            + formatDouble(result.peakUsedRamMiB) + ","
            + iterations + ","
            + warmup + ","
            + quoteCsv(timestamp) + "\n";
    }

    private void writeFailure(String plugin, String pluginVersion, String storage, String redis,
                              int warmup, int iterations, String reason) {
        File outDir = new File(getDataFolder(), "results");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        File jsonFile = new File(outDir, "result.json");
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
        String json = "{\n"
            + "  \"status\": \"failed\",\n"
            + "  \"plugin\": \"" + escape(plugin) + "\",\n"
            + "  \"pluginVersion\": \"" + escape(pluginVersion) + "\",\n"
            + "  \"storage\": \"" + escape(storage) + "\",\n"
            + "  \"redis\": \"" + escape(redis) + "\",\n"
            + "  \"warmupIterations\": " + warmup + ",\n"
            + "  \"measureIterations\": " + iterations + ",\n"
            + "  \"timestamp\": \"" + escape(now) + "\",\n"
            + "  \"reason\": \"" + escape(reason) + "\"\n"
            + "}\n";
        writeText(jsonFile, json);
    }

    private static void writeText(File file, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + file.getAbsolutePath(), e);
        }
    }

    private static String envOrDefault(String key, String def) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        return value.trim();
    }

    private static int intEnv(String key, int def) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String quoteCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private void shutdown() {
        Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
    }

    // ---- Bank benchmarks ----

    private void runBankBenchmarks(Economy economy, String activeProvider,
            String pluginUnderTest, String pluginVersion, String storageMode, String redisMode,
            int warmupIterations, int measureIterations, String runId, String runAttempt) {
        long runStartNanos = System.nanoTime();
        if (!economy.hasBankSupport()) {
            writeBankSkip(pluginUnderTest, pluginVersion, storageMode, redisMode,
                "Vault bank API not supported by this economy provider");
            return;
        }

        org.bukkit.OfflinePlayer bankOwner = Bukkit.getOfflinePlayer(
            UUID.fromString("22222222-2222-2222-2222-222222222222"));
        String bankName = "ez-bench";

        economy.deleteBank(bankName);
        economy.createBank(bankName, bankOwner);

        try {
            BenchResult bankDeposit = benchBankDeposit(economy, bankName, warmupIterations, measureIterations);
            BenchResult bankWithdraw = benchBankWithdraw(economy, bankName, warmupIterations, measureIterations);
            BenchResult bankBalanceHas = benchBankBalanceHas(economy, bankName, warmupIterations, measureIterations);
            long runDurationMs = (System.nanoTime() - runStartNanos) / 1_000_000L;

            writeBankOutputs(pluginUnderTest, pluginVersion, storageMode, redisMode, warmupIterations,
                measureIterations, runId, runAttempt, activeProvider, bankDeposit, bankWithdraw, bankBalanceHas, runDurationMs);
        } catch (Exception ex) {
            writeBankFailure(pluginUnderTest, pluginVersion, storageMode, redisMode, warmupIterations,
                measureIterations, "Bank benchmark failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            getLogger().warning("Bank benchmark failed");
            ex.printStackTrace();
        } finally {
            economy.deleteBank(bankName);
        }
    }

    private static BenchResult benchBankDeposit(Economy economy, String bankName, int warmup, int iterations) {
        for (int i = 0; i < warmup; i++) {
            bankDeposit(economy, bankName, 1.0D);
        }
        long totalNanos = 0L;
        long[] samples = new long[iterations];
        RamStats ramStats = new RamStats();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            bankDeposit(economy, bankName, 1.0D);
            long elapsed = System.nanoTime() - start;
            samples[i] = elapsed;
            totalNanos += elapsed;
            ramStats.capture();
        }
        return BenchResult.from("bank_deposit", totalNanos, samples, ramStats);
    }

    private static BenchResult benchBankWithdraw(Economy economy, String bankName, int warmup, int iterations) {
        bankDeposit(economy, bankName, warmup + iterations + 100.0D);
        for (int i = 0; i < warmup; i++) {
            bankWithdraw(economy, bankName, 1.0D);
        }
        long totalNanos = 0L;
        long[] samples = new long[iterations];
        RamStats ramStats = new RamStats();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            bankWithdraw(economy, bankName, 1.0D);
            long elapsed = System.nanoTime() - start;
            samples[i] = elapsed;
            totalNanos += elapsed;
            ramStats.capture();
        }
        return BenchResult.from("bank_withdraw", totalNanos, samples, ramStats);
    }

    private static BenchResult benchBankBalanceHas(Economy economy, String bankName, int warmup, int iterations) {
        bankDeposit(economy, bankName, 1000.0D);
        for (int i = 0; i < warmup; i++) {
            bankBalance(economy, bankName);
            bankHas(economy, bankName, 1.0D);
        }
        long totalNanos = 0L;
        long[] samples = new long[iterations];
        RamStats ramStats = new RamStats();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            bankBalance(economy, bankName);
            bankHas(economy, bankName, 1.0D);
            long elapsed = System.nanoTime() - start;
            samples[i] = elapsed;
            totalNanos += elapsed;
            ramStats.capture();
        }
        return BenchResult.from("bank_balance_has", totalNanos, samples, ramStats);
    }

    private static void bankDeposit(Economy economy, String bankName, double amount) {
        economy.bankDeposit(bankName, amount);
    }

    private static void bankWithdraw(Economy economy, String bankName, double amount) {
        economy.bankWithdraw(bankName, amount);
    }

    private static double bankBalance(Economy economy, String bankName) {
        EconomyResponse resp = economy.bankBalance(bankName);
        return resp != null ? resp.balance : 0.0D;
    }

    private static boolean bankHas(Economy economy, String bankName, double amount) {
        EconomyResponse resp = economy.bankHas(bankName, amount);
        return resp != null && resp.transactionSuccess();
    }

    private void writeBankOutputs(String plugin, String pluginVersion, String storage, String redis,
                                  int warmup, int iterations, String runId, String runAttempt, String provider,
                                  BenchResult bankDeposit, BenchResult bankWithdraw, BenchResult bankBalanceHas, long runtimeMs) {
        File outDir = new File(getDataFolder(), "results");
        if (!outDir.exists() && !outDir.mkdirs()) {
            getLogger().warning("Failed to create results directory: " + outDir.getAbsolutePath());
            return;
        }

        File jsonFile = new File(outDir, "result-bank.json");
        File csvFile = new File(outDir, "result-bank.csv");

        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());

        String json = "{\n"
            + "  \"status\": \"ok\",\n"
            + "  \"plugin\": \"" + escape(plugin) + "\",\n"
            + "  \"pluginVersion\": \"" + escape(pluginVersion) + "\",\n"
            + "  \"activeVaultProvider\": \"" + escape(provider) + "\",\n"
            + "  \"storage\": \"" + escape(storage) + "\",\n"
            + "  \"redis\": \"" + escape(redis) + "\",\n"
            + "  \"warmupIterations\": " + warmup + ",\n"
            + "  \"measureIterations\": " + iterations + ",\n"
            + "  \"runtimeMs\": " + runtimeMs + ",\n"
            + "  \"runId\": \"" + escape(runId) + "\",\n"
            + "  \"runAttempt\": \"" + escape(runAttempt) + "\",\n"
            + "  \"timestamp\": \"" + escape(now) + "\",\n"
            + "  \"metrics\": {\n"
            + metricJson("bank_deposit", bankDeposit) + ",\n"
            + metricJson("bank_withdraw", bankWithdraw) + ",\n"
            + metricJson("bank_balance_has", bankBalanceHas) + "\n"
            + "  }\n"
            + "}\n";

        writeText(jsonFile, json);

        String csv = "plugin,plugin_version,active_provider,storage,redis,operation,average_ns,average_ms,p95_ns,p95_ms,avg_used_ram_bytes,avg_used_ram_mib,peak_used_ram_bytes,peak_used_ram_mib,iterations,warmup,timestamp\n"
            + csvLine(plugin, pluginVersion, provider, storage, redis, bankDeposit, iterations, warmup, now)
            + csvLine(plugin, pluginVersion, provider, storage, redis, bankWithdraw, iterations, warmup, now)
            + csvLine(plugin, pluginVersion, provider, storage, redis, bankBalanceHas, iterations, warmup, now);

        writeText(csvFile, csv);
        getLogger().info("Bank benchmark results written to " + outDir.getAbsolutePath());
    }

    private void writeBankSkip(String plugin, String pluginVersion, String storage, String redis, String reason) {
        File outDir = new File(getDataFolder(), "results");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        File jsonFile = new File(outDir, "result-bank.json");
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
        String json = "{\n"
            + "  \"status\": \"skipped\",\n"
            + "  \"plugin\": \"" + escape(plugin) + "\",\n"
            + "  \"pluginVersion\": \"" + escape(pluginVersion) + "\",\n"
            + "  \"storage\": \"" + escape(storage) + "\",\n"
            + "  \"redis\": \"" + escape(redis) + "\",\n"
            + "  \"timestamp\": \"" + escape(now) + "\",\n"
            + "  \"reason\": \"" + escape(reason) + "\"\n"
            + "}\n";
        writeText(jsonFile, json);
        getLogger().info("Bank benchmark skipped: " + reason);
    }

    private void writeBankFailure(String plugin, String pluginVersion, String storage, String redis,
                                  int warmup, int iterations, String reason) {
        File outDir = new File(getDataFolder(), "results");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        File jsonFile = new File(outDir, "result-bank.json");
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
        String json = "{\n"
            + "  \"status\": \"failed\",\n"
            + "  \"plugin\": \"" + escape(plugin) + "\",\n"
            + "  \"pluginVersion\": \"" + escape(pluginVersion) + "\",\n"
            + "  \"storage\": \"" + escape(storage) + "\",\n"
            + "  \"redis\": \"" + escape(redis) + "\",\n"
            + "  \"warmupIterations\": " + warmup + ",\n"
            + "  \"measureIterations\": " + iterations + ",\n"
            + "  \"timestamp\": \"" + escape(now) + "\",\n"
            + "  \"reason\": \"" + escape(reason) + "\"\n"
            + "}\n";
        writeText(jsonFile, json);
    }

    private static final class BenchResult {
        private final String operation;
        private final long averageNs;
        private final double averageMs;
        private final long p95Ns;
        private final double p95Ms;
        private final long avgUsedRamBytes;
        private final double avgUsedRamMiB;
        private final long peakUsedRamBytes;
        private final double peakUsedRamMiB;

        private BenchResult(String operation, long averageNs, long p95Ns, long avgUsedRamBytes, long peakUsedRamBytes) {
            this.operation = operation;
            this.averageNs = averageNs;
            this.averageMs = averageNs / 1_000_000.0D;
            this.p95Ns = p95Ns;
            this.p95Ms = p95Ns / 1_000_000.0D;
            this.avgUsedRamBytes = avgUsedRamBytes;
            this.avgUsedRamMiB = avgUsedRamBytes / 1024.0D / 1024.0D;
            this.peakUsedRamBytes = peakUsedRamBytes;
            this.peakUsedRamMiB = peakUsedRamBytes / 1024.0D / 1024.0D;
        }

        private static BenchResult from(String operation, long totalNanos, long[] samples, RamStats ramStats) {
            long average = totalNanos / samples.length;
            long p95 = percentile(samples, 95);
            return new BenchResult(operation, average, p95, ramStats.averageBytes(), ramStats.peakBytes());
        }

        private static long percentile(long[] values, int pct) {
            long[] copy = values.clone();
            java.util.Arrays.sort(copy);
            int index = (int) Math.ceil((pct / 100.0D) * copy.length) - 1;
            if (index < 0) {
                index = 0;
            }
            if (index >= copy.length) {
                index = copy.length - 1;
            }
            return copy[index];
        }
    }

    private static final class RamStats {
        private long sampleCount;
        private long sampleTotal;
        private long peak;

        private void capture() {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            sampleCount++;
            sampleTotal += used;
            if (used > peak) {
                peak = used;
            }
        }

        private long averageBytes() {
            if (sampleCount == 0L) {
                return 0L;
            }
            return sampleTotal / sampleCount;
        }

        private long peakBytes() {
            return peak;
        }
    }
}
