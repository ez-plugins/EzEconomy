package com.skyblockexp.ezeconomy.storage.mysql;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class StripedLockManagerTest {

    @Test
    void sameKeyReturnsSameLockObject() {
        StripedLockManager mgr = new StripedLockManager(16);
        UUID u = UUID.randomUUID();
        Object a = mgr.lockFor(u);
        Object b = mgr.lockFor(u);
        assertNotNull(a);
        assertSame(a, b, "Repeated lockFor for same UUID should return identical lock object");
    }

    @Test
    void nullKeyReturnsDefaultStripe() {
        StripedLockManager mgr = new StripedLockManager(8);
        Object n1 = mgr.lockFor((String) null);
        Object n2 = mgr.lockFor((String) null);
        assertNotNull(n1);
        assertSame(n1, n2);
    }
}
