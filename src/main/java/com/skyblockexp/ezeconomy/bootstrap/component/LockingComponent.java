package com.skyblockexp.ezeconomy.bootstrap.component;

import com.skyblockexp.ezeconomy.bootstrap.BootstrapComponent;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.lock.LockManager;
import com.skyblockexp.ezeconomy.lock.LocalLockManager;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import java.util.Iterator;
import java.io.IOException;
import java.util.function.Supplier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import com.skyblockexp.ezeconomy.cache.CacheManager;
import com.skyblockexp.ezeconomy.cache.CachingStrategy;

public class LockingComponent implements BootstrapComponent {
    private final EzEconomyPlugin plugin;
    private LockManager manager;

    public LockingComponent(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        FileConfiguration cfg = plugin.getConfig();
        // Configure cache strategy early from config (backwards-compatible with 'caching-strategy')
        String caching = cfg.getString("caching-strategy", cfg.getString("locking-strategy", "LOCAL")).toUpperCase();
        try {
            CacheManager.setStrategy(CachingStrategy.valueOf(caching));
            plugin.getLogger().info("Cache strategy set to " + caching);
        } catch (IllegalArgumentException ia) {
            CacheManager.setStrategy(CachingStrategy.LOCAL);
            plugin.getLogger().warning("Unknown caching-strategy '" + caching + "', defaulting to LOCAL");
        }
        String strategy = cfg.getString("locking-strategy", "LOCAL").toUpperCase();
        // Load redis.yml from data folder (save default resource if missing)
        File redisFile = new File(plugin.getDataFolder(), "redis.yml");
        if (!redisFile.exists()) {
            try { plugin.saveResource("redis.yml", false); } catch (Exception ignored) {}
        }
        FileConfiguration redisCfg = YamlConfiguration.loadConfiguration(redisFile);

        // Load bungeecord.yml from data folder (save default resource if missing)
        File bungeeFile = new File(plugin.getDataFolder(), "bungeecord.yml");
        if (!bungeeFile.exists()) {
            try { plugin.saveResource("bungeecord.yml", false); } catch (Exception ignored) {}
        }
        FileConfiguration bungeeCfg = YamlConfiguration.loadConfiguration(bungeeFile);

        if ("NONE".equals(strategy) || "OFF".equals(strategy) || "DISABLED".equals(strategy)) {
            this.manager = null;
            plugin.getLogger().info("Locking strategy disabled by configuration.");
        } else if ("BUNGEECORD".equals(strategy) && bungeeCfg.getBoolean("enabled", false)) {
            boolean fallback = bungeeCfg.getBoolean("fallback-to-local", true);
            try {
                // 1) Try ServiceLoader using current classloader (useful for development/classpath-loaded providers)
                this.manager = safeLoadFirstLockManager(() -> ServiceLoader.load(LockManager.class).iterator(), "current classloader");
                if (this.manager != null) {
                    plugin.getLogger().info("Loaded LockManager via ServiceLoader: " + this.manager.getClass().getName());
                }

                // 2) If not found, try loading jars from plugin data 'libs' directory
                if (this.manager == null) {
                    File libs = new File(plugin.getDataFolder(), "libs");
                    if (libs.exists() && libs.isDirectory()) {
                        File[] jars = libs.listFiles((d, name) -> name.endsWith(".jar"));
                        if (jars != null) {
                            for (File jar : jars) {
                                try (URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()}, this.getClass().getClassLoader())) {
                                    this.manager = safeLoadFirstLockManager(() -> ServiceLoader.load(LockManager.class, cl).iterator(), "jar " + jar.getName());
                                    if (this.manager != null) {
                                        plugin.getLogger().info("Loaded LockManager from " + jar.getName() + ": " + this.manager.getClass().getName());
                                        break;
                                    }
                                } catch (IOException e) {
                                    plugin.getLogger().warning("Failed to load extension jar " + jar.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                }

                // 3) If still not found, attempt reflection instantiation (for cases where class is on classpath)
                if (this.manager == null) {
                    try {
                        Class<?> clazz = Class.forName("com.skyblockexp.ezeconomy.lock.BungeeLockManager");
                        Object inst = clazz.getConstructor().newInstance();
                        this.manager = (LockManager) inst;
                        plugin.getLogger().info("Initialized BungeeLockManager via reflection: " + this.manager.getClass().getName());

                        // Attempt to wire a PluginMessagingTransport if available on the classpath
                        try {
                            Class<?> transportClazz = Class.forName("com.skyblockexp.ezeconomy.lock.transport.PluginMessagingTransport");
                            Object transport = null;
                            try {
                                // preferred constructor: (Plugin, String, long, String)
                                transport = transportClazz.getConstructor(org.bukkit.plugin.Plugin.class, String.class, long.class, String.class)
                                        .newInstance(plugin, bungeeCfg.getString("channel", "ezeconomy:locks"), bungeeCfg.getLong("ttl-ms", 60000L), bungeeCfg.getString("shared-secret", ""));
                            } catch (NoSuchMethodException ns) {
                                // fallback to older constructor (Plugin, String, long)
                                transport = transportClazz.getConstructor(org.bukkit.plugin.Plugin.class, String.class, long.class)
                                        .newInstance(plugin, bungeeCfg.getString("channel", "ezeconomy:locks"), bungeeCfg.getLong("ttl-ms", 60000L));
                            }
                            // call BungeeLockManager.setGlobalTransport(transport)
                            Class<?> bmClazz = Class.forName("com.skyblockexp.ezeconomy.lock.BungeeLockManager");
                            java.lang.reflect.Method setGlobal = bmClazz.getMethod("setGlobalTransport", Class.forName("com.skyblockexp.ezeconomy.lock.transport.LockTransport"));
                            setGlobal.invoke(null, transport);
                            plugin.getLogger().info("Wired PluginMessagingTransport for BungeeLockManager");
                        } catch (ClassNotFoundException cnf) {
                            // transport not available on classpath; ignore
                        }
                    } catch (ClassNotFoundException cnf) {
                        // no-op
                    }
                }

                if (this.manager == null) throw new RuntimeException("No Bungee LockManager provider found");
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to initialize BungeeLockManager: " + ex.getMessage());
                if (fallback) {
                    this.manager = new LocalLockManager();
                    plugin.getLogger().info("Falling back to LocalLockManager.");
                } else {
                    throw new RuntimeException("Bungeecord locking initialization failed and fallback disabled", ex);
                }
            }
        } else if ("REDIS".equals(strategy) && redisCfg.getBoolean("enabled", false)) {
            // Read Redis configuration exclusively from redis.yml (do not read Redis keys from config.yml)
            String host = redisCfg.getString("host", "localhost");
            int port = redisCfg.getInt("port", 6379);
            String password = redisCfg.getString("password", "");
            int database = redisCfg.getInt("database", 0);
            boolean fallback = redisCfg.getBoolean("fallback-to-local", true);
            try {
                // 1) Try ServiceLoader using current classloader (useful for development/classpath-loaded providers)
                this.manager = safeLoadFirstLockManager(() -> ServiceLoader.load(LockManager.class).iterator(), "current classloader");
                if (this.manager != null) {
                    plugin.getLogger().info("Loaded LockManager via ServiceLoader: " + this.manager.getClass().getName());
                }

                // 2) If not found, try loading jars from plugin data 'libs' directory
                if (this.manager == null) {
                    File libs = new File(plugin.getDataFolder(), "libs");
                    if (libs.exists() && libs.isDirectory()) {
                        File[] jars = libs.listFiles((d, name) -> name.endsWith(".jar"));
                        if (jars != null) {
                            for (File jar : jars) {
                                try (URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()}, this.getClass().getClassLoader())) {
                                    this.manager = safeLoadFirstLockManager(() -> ServiceLoader.load(LockManager.class, cl).iterator(), "jar " + jar.getName());
                                    if (this.manager != null) {
                                        plugin.getLogger().info("Loaded LockManager from " + jar.getName() + ": " + this.manager.getClass().getName());
                                        break;
                                    }
                                } catch (IOException e) {
                                    plugin.getLogger().warning("Failed to load extension jar " + jar.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                    }
                }

                // 3) If still not found, attempt reflection instantiation (for cases where class is on classpath)
                if (this.manager == null) {
                    try {
                        Class<?> clazz = Class.forName("com.skyblockexp.ezeconomy.lock.RedisLockManager");
                        java.lang.reflect.Constructor<?> ctor = clazz.getConstructor(String.class, int.class, String.class, int.class);
                        Object inst = ctor.newInstance(host, port, password, database);
                        this.manager = (LockManager) inst;
                        plugin.getLogger().info("Initialized RedisLockManager via reflection: " + this.manager.getClass().getName());
                    } catch (ClassNotFoundException cnf) {
                        // no-op
                    }
                }

                if (this.manager == null) throw new RuntimeException("No Redis LockManager provider found");
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to initialize RedisLockManager: " + ex.getMessage());
                if (fallback) {
                    this.manager = new LocalLockManager();
                    plugin.getLogger().info("Falling back to LocalLockManager.");
                } else {
                    throw new RuntimeException("Redis locking initialization failed and fallback disabled", ex);
                }
            }
        } else {
            this.manager = new LocalLockManager();
            plugin.getLogger().info("Using LocalLockManager for balance locking.");
        }
        plugin.setLockManager(this.manager);
    }

    private LockManager safeLoadFirstLockManager(Supplier<Iterator<LockManager>> iteratorSupplier, String source) {
        try {
            Iterator<LockManager> it = iteratorSupplier.get();
            if (it != null && it.hasNext()) {
                return it.next();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Ignoring invalid LockManager provider from " + source + ": " + t.getMessage());
        }
        return null;
    }

    @Override
    public void stop() {
        plugin.setLockManager(null);
        if (this.manager instanceof AutoCloseable) {
            try { ((AutoCloseable) this.manager).close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void reload() {
        stop();
        start();
    }
}
