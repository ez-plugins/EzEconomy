package com.skyblockexp.ezeconomy.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessagingServiceTest {

    @Test
    void messageType_valuesExist() {
        MessageType[] values = MessageType.values();
        assertEquals(3, values.length);
        assertNotNull(MessageType.valueOf("NOTIFY"));
        assertNotNull(MessageType.valueOf("RECIPIENT_OFFLINE"));
        assertNotNull(MessageType.valueOf("PLAYER_LIST"));
    }

    @Test
    void messagingTransport_interfaceHasMethods() throws Exception {
        assertNotNull(MessagingTransport.class.getMethod("sendNotification",
                java.util.UUID.class, String.class, String.class, String.class, String.class));
        assertNotNull(MessagingTransport.class.getMethod("shutdown"));
    }

    @Test
    void messagingService_channelConstant() {
        assertEquals("ezeconomy:notify", MessagingService.CHANNEL);
    }
}
