package com.skyblockexp.ezeconomy.messaging;

public interface MessagingTransport {
	void sendNotification(java.util.UUID recipientUuid, String recipientName, String senderName, String amount, String currency);
	void shutdown();
}
