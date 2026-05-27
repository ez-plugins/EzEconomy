package com.skyblockexp.ezeconomy.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutorTest {

    private Object server;

    @BeforeEach
    void setup() throws Exception {
        try {
            server = MockBukkit.mock();
        } catch (IllegalStateException e) {
            MockBukkit.unmock();
            server = MockBukkit.mock();
        }
    }

    @AfterEach
    void teardown() {
        try { MockBukkit.unmock(); } catch (Exception ignored) {}
    }

    @Test
    void execute_nullFrom_returnsFalse() {
        boolean res = PaymentExecutor.execute(null, null, "target", BigDecimal.ONE, "dollar");
        assertFalse(res);
    }

    @Test
    void execute_nullToName_returnsFalse() throws Exception {
        Object p = server.getClass().getMethod("addPlayer", String.class).invoke(server, "dummy");
        PlayerMock player = (PlayerMock) p;
        boolean res = PaymentExecutor.execute(null, player, null, BigDecimal.ONE, "dollar");
        assertFalse(res);
    }

    @Test
    void execute_nullAmount_returnsFalse() throws Exception {
        Object p = server.getClass().getMethod("addPlayer", String.class).invoke(server, "dummy2");
        PlayerMock player = (PlayerMock) p;
        boolean res = PaymentExecutor.execute(null, player, "target", null, "dollar");
        assertFalse(res);
    }
}
