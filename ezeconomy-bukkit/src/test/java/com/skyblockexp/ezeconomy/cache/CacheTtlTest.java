package com.skyblockexp.ezeconomy.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CacheTtlTest {

    @Test
    void entriesExpireAfterTtl() throws Exception {
        CacheProvider<String, Double> cache = CacheManager.getProvider();
        String key = "ttl:test";
        cache.put(key, 42.0, 200);
        ExpiringCache.Entry<Double> e = cache.getEntry(key);
        assertNotNull(e);
        assertEquals(42.0, e.value, 0.0001);
        long expiresAt = e.expiresAt;
        assertTrue(expiresAt > System.currentTimeMillis());

        // wait for expiry
        Thread.sleep(350);

        ExpiringCache.Entry<Double> e2 = cache.getEntry(key);
        // ExpiringCache itself does not proactively remove entries; callers check expiresAt.
        assertNotNull(e2);
        assertTrue(e2.expiresAt <= System.currentTimeMillis(), "Entry should be expired");
    }
}
