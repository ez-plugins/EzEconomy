package com.skyblockexp.ezeconomy.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferResultTest {

    @Test
    void successFactorySetsValues() {
        TransferResult r = TransferResult.success(10.0, 20.0);
        assertTrue(r.isSuccess());
        assertEquals(10.0, r.getFromBalance());
        assertEquals(20.0, r.getToBalance());
    }

    @Test
    void failureFactorySetsValues() {
        TransferResult r = TransferResult.failure(5.5, 6.6);
        assertFalse(r.isSuccess());
        assertEquals(5.5, r.getFromBalance());
        assertEquals(6.6, r.getToBalance());
    }
}
