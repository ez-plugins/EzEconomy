package com.skyblockexp.ezeconomy.example;

import com.skyblockexp.ezeconomy.api.events.PlayerPayPlayerEvent;
import com.skyblockexp.ezeconomy.api.events.PreTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.PostTransactionEvent;
import com.skyblockexp.ezeconomy.api.events.TransactionType;
import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Example listener showing how to use EzEconomy transaction events.
 * - Cancels payments over a configured limit
 * - Logs successful pay transfers
 */
public class PaymentEventListener implements Listener {
    private final EzEconomyPlugin plugin;
    private final BigDecimal limit = new BigDecimal("10000");

    public PaymentEventListener(EzEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPay(PlayerPayPlayerEvent e) {
        BigDecimal amt = e.getAmount();
        if (amt.compareTo(limit) > 0) {
            e.setCancelled(true);
            e.setCancelReason(plugin.getMessageProvider().color(plugin.getMessageProvider().get("not_enough_money")));
            plugin.getLogger().info("Blocked large payment from " + e.getSource() + " to " + e.getTarget() + " amount=" + amt);
        }
    }

    @EventHandler
    public void onPost(PostTransactionEvent e) {
        if (!e.isSuccess()) return;
        if (e.getType() == TransactionType.PAY) {
            UUID from = e.getSource();
            UUID to = e.getTarget();
            plugin.getLogger().info("Payment succeeded: " + from + " -> " + to + " amount=" + e.getAmount());
        }
    }
}
