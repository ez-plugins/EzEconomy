package com.skyblockexp.ezeconomy.service;

import com.skyblockexp.ezeconomy.core.EzEconomyPlugin;
import com.skyblockexp.ezeconomy.util.MessageUtils;
import com.skyblockexp.ezeconomy.util.CurrencyUtil;
import com.skyblockexp.ezeconomy.storage.TransferLockManager;
import com.skyblockexp.ezeconomy.storage.TransferResult;
import com.skyblockexp.ezeconomy.api.events.PlayerPayPlayerEvent;
import com.skyblockexp.ezeconomy.api.storage.StorageProvider;
import com.skyblockexp.ezeconomy.util.EventDispatcher;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentExecutor {
    /**
     * Execute a payment between players. Returns true if the operation completed (success or handled failure).
     */
    public static boolean execute(EzEconomyPlugin plugin, Player from, String toName, BigDecimal amountDecimal, String currency) {
        return execute(plugin, from, toName, amountDecimal, currency, false);
    }

    public static boolean execute(EzEconomyPlugin plugin, Player from, String toName, BigDecimal amountDecimal, String currency, boolean knownOffline) {
        if (from == null || toName == null || amountDecimal == null) return false;
        double netAmount = amountDecimal.doubleValue();
        StorageProvider storage = plugin.getStorageOrWarn();
        if (storage == null) return true;
        plugin.getLogger().info("PaymentExecutor: start execute from=" + from.getName() + " toName=" + toName + " amount=" + netAmount + " currency=" + currency + " knownOffline=" + knownOffline);

        Player online = Bukkit.getPlayerExact(toName);
        OfflinePlayer toOffline = online != null ? online : null;

        UUID fromUuid = from.getUniqueId();

        if (online == null) {
            // Try local Bukkit lookup first
            OfflinePlayer localOffline = Bukkit.getOfflinePlayer(toName);
            if (localOffline != null && localOffline.hasPlayedBefore()) {
                toOffline = localOffline;
            }

            // If not found locally, resolve from shared database or messaging service
            if (toOffline == null && knownOffline) {
                UUID resolvedUuid = null;
                com.skyblockexp.ezeconomy.messaging.MessagingService ms = plugin.getMessagingService();
                if (ms != null) {
                    resolvedUuid = ms.getNetworkPlayerUuid(toName);
                }
                if (resolvedUuid == null) {
                    StorageProvider storageForResolve = plugin.getStorageOrWarn();
                    if (storageForResolve != null) {
                        resolvedUuid = storageForResolve.resolvePlayerByName(toName);
                    }
                }
                if (resolvedUuid != null) {
                    toOffline = Bukkit.getOfflinePlayer(resolvedUuid);
                }
            }

            if (toOffline == null) {
                plugin.getLogger().info("PaymentExecutor: recipient not found, aborting");
                MessageUtils.send(from, plugin, "player_not_found");
                return true;
            }
        }
        plugin.getLogger().info("PaymentExecutor: executing transfer from=" + from.getName() + " to=" + toName + " online=" + (online!=null));

        if (toOffline.getUniqueId().equals(fromUuid)) {
            MessageUtils.send(from, plugin, "cannot_pay_self");
            return true;
        }

        // Fire event (must be synchronous)
        final PlayerPayPlayerEvent payEvent = new PlayerPayPlayerEvent(fromUuid, toOffline.getUniqueId(), amountDecimal);
        EventDispatcher.fireSync(plugin, payEvent);
        if (payEvent.isCancelled()) {
            String reason = payEvent.getCancelReason();
            if (reason != null && !reason.isEmpty()) {
                from.sendMessage(reason);
            } else {
                MessageUtils.send(from, plugin, "payment_cancelled");
            }
            return true;
        }

        // Recipient currency preference
        String recipientCurrency = plugin.getCurrencyPreferenceManager().getPreferredCurrency(toOffline.getUniqueId());
        if (recipientCurrency != null && !recipientCurrency.equalsIgnoreCase(currency)) {
            UUID toUuid = toOffline.getUniqueId();
            UUID first = fromUuid.compareTo(toUuid) <= 0 ? fromUuid : toUuid;
            UUID second = fromUuid.compareTo(toUuid) <= 0 ? toUuid : fromUuid;

            // Prefer distributed locking if available
            com.skyblockexp.ezeconomy.lock.LockManager lm = plugin.getLockManager();
            if (lm != null) {
                UUID[] uuids = new UUID[]{first, second};
                String[] tokens = null;
                try {
                    tokens = lm.acquireOrdered(uuids, 5000L, 50L, 100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    tokens = null;
                }
                if (tokens != null) {
                    try {
                        double fromBalance = storage.getBalance(fromUuid, currency);
                        plugin.getLogger().info("PaymentExecutor: currency-preference path fromBalance=" + fromBalance + " netAmount=" + netAmount);
                        if (fromBalance < netAmount) {
                            plugin.getLogger().info("PaymentExecutor: not enough money (balance < amount)");
                            MessageUtils.send(from, plugin, "not_enough_money");
                            return true;
                        }
                        boolean withdrew = storage.tryWithdraw(fromUuid, currency, netAmount);
                        plugin.getLogger().info("PaymentExecutor: tryWithdraw result=" + withdrew);
                        if (!withdrew) {
                            plugin.getLogger().info("PaymentExecutor: withdrawFailed, aborting");
                            MessageUtils.send(from, plugin, "not_enough_money");
                            return true;
                        }
                        double creditAmount = CurrencyUtil.convert(plugin, netAmount, currency, recipientCurrency);
                        if (Double.isNaN(creditAmount)) {
                            storage.deposit(fromUuid, currency, netAmount);
                            MessageUtils.send(from, plugin, "unknown_currency", java.util.Map.of("currency", recipientCurrency));
                            return true;
                        }
                        plugin.getLogger().info("PaymentExecutor: depositing " + creditAmount + " " + recipientCurrency + " to " + toOffline.getUniqueId());
                        storage.deposit(toOffline.getUniqueId(), recipientCurrency, creditAmount);

                        String payerDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(netAmount, currency);
                        String receiverDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(creditAmount, recipientCurrency);
                        String defaultCur = plugin.getDefaultCurrency();
                        if (!currency.equalsIgnoreCase(defaultCur)) {
                            double equiv = CurrencyUtil.convert(plugin, netAmount, currency, defaultCur);
                            if (!Double.isNaN(equiv)) {
                                String equivDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(equiv, defaultCur);
                                MessageUtils.send(from, plugin, "paid_other_currency", java.util.Map.of("player", toOffline.getName(), "amount", payerDisplay, "amount_default", equivDisplay));
                            } else {
                                MessageUtils.send(from, plugin, "paid", java.util.Map.of("player", toOffline.getName(), "amount", payerDisplay));
                            }
                        } else {
                            MessageUtils.send(from, plugin, "paid", java.util.Map.of("player", toOffline.getName(), "amount", payerDisplay));
                        }
                        if (toOffline.isOnline() && toOffline.getPlayer() != null) {
                            if (!currency.equalsIgnoreCase(defaultCur)) {
                                double equiv = CurrencyUtil.convert(plugin, creditAmount, recipientCurrency, defaultCur);
                                if (!Double.isNaN(equiv)) {
                                    String equivDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(equiv, defaultCur);
                                    MessageUtils.send(toOffline.getPlayer(), plugin, "received_other_currency", java.util.Map.of("player", from.getName(), "amount", receiverDisplay, "amount_default", equivDisplay));
                                } else {
                                    MessageUtils.send(toOffline.getPlayer(), plugin, "received", java.util.Map.of("player", from.getName(), "amount", receiverDisplay));
                                }
                            } else {
                                MessageUtils.send(toOffline.getPlayer(), plugin, "received", java.util.Map.of("player", from.getName(), "amount", receiverDisplay));
                            }
                        } else {
                            notifyCrossServer(plugin, toOffline.getUniqueId(), toOffline.getName(), from.getName(), receiverDisplay, recipientCurrency);
                        }
                        return true;
                    } finally {
                        lm.releaseOrdered(uuids, tokens);
                    }
                }
                // fallthrough to local locking
            }

            // Fallback: local in-JVM locks
            UUID firstLocal = first;
            UUID secondLocal = second;
            java.util.concurrent.locks.ReentrantLock firstLock = TransferLockManager.getLock(firstLocal);
            java.util.concurrent.locks.ReentrantLock secondLock = firstLocal.equals(secondLocal) ? firstLock : TransferLockManager.getLock(secondLocal);
            firstLock.lock();
            if (!firstLocal.equals(secondLocal)) secondLock.lock();
            try {
                double fromBalance = storage.getBalance(fromUuid, currency);
                plugin.getLogger().info("PaymentExecutor: currency-preference path fromBalance=" + fromBalance + " netAmount=" + netAmount);
                if (fromBalance < netAmount) {
                    plugin.getLogger().info("PaymentExecutor: not enough money (balance < amount)");
                    MessageUtils.send(from, plugin, "not_enough_money");
                    return true;
                }
                boolean withdrew = storage.tryWithdraw(fromUuid, currency, netAmount);
                plugin.getLogger().info("PaymentExecutor: tryWithdraw result=" + withdrew);
                if (!withdrew) {
                    plugin.getLogger().info("PaymentExecutor: withdraw failed, aborting");
                    MessageUtils.send(from, plugin, "not_enough_money");
                    return true;
                }
                double creditAmount = CurrencyUtil.convert(plugin, netAmount, currency, recipientCurrency);
                if (Double.isNaN(creditAmount)) {
                    storage.deposit(fromUuid, currency, netAmount);
                    MessageUtils.send(from, plugin, "unknown_currency", java.util.Map.of("currency", recipientCurrency));
                    return true;
                }
                plugin.getLogger().info("PaymentExecutor: depositing " + creditAmount + " " + recipientCurrency + " to " + toOffline.getUniqueId());
                storage.deposit(toOffline.getUniqueId(), recipientCurrency, creditAmount);

                String payerDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(netAmount, currency);
                String receiverDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(creditAmount, recipientCurrency);
                String defaultCur = plugin.getDefaultCurrency();
                if (!currency.equalsIgnoreCase(defaultCur)) {
                    double equiv = CurrencyUtil.convert(plugin, netAmount, currency, defaultCur);
                    if (!Double.isNaN(equiv)) {
                        String equivDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(equiv, defaultCur);
                        MessageUtils.send(from, plugin, "paid_other_currency", java.util.Map.of("player", toOffline.getName(), "amount", payerDisplay, "amount_default", equivDisplay));
                    } else {
                        MessageUtils.send(from, plugin, "paid", java.util.Map.of("player", toOffline.getName(), "amount", payerDisplay));
                    }
                } else {
                    MessageUtils.send(from, plugin, "paid", java.util.Map.of("player", toOffline.getName(), "amount", payerDisplay));
                }
                if (toOffline.isOnline() && toOffline.getPlayer() != null) {
                    if (!currency.equalsIgnoreCase(defaultCur)) {
                        double equiv = CurrencyUtil.convert(plugin, creditAmount, recipientCurrency, defaultCur);
                        if (!Double.isNaN(equiv)) {
                            String equivDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(equiv, defaultCur);
                            MessageUtils.send(toOffline.getPlayer(), plugin, "received_other_currency", java.util.Map.of("player", from.getName(), "amount", receiverDisplay, "amount_default", equivDisplay));
                        } else {
                            MessageUtils.send(toOffline.getPlayer(), plugin, "received", java.util.Map.of("player", from.getName(), "amount", receiverDisplay));
                        }
                    } else {
                        MessageUtils.send(toOffline.getPlayer(), plugin, "received", java.util.Map.of("player", from.getName(), "amount", receiverDisplay));
                    }
                } else {
                    notifyCrossServer(plugin, toOffline.getUniqueId(), toOffline.getName(), from.getName(), receiverDisplay, recipientCurrency);
                }
                return true;
            } finally {
                if (!firstLocal.equals(secondLocal)) secondLock.unlock();
                firstLock.unlock();
            }
        }

        // Simple transfer
        plugin.getLogger().info("PaymentExecutor: performing simple transfer via storage.transfer");
        TransferResult transfer = storage.transfer(fromUuid, toOffline.getUniqueId(), currency, netAmount);
        plugin.getLogger().info("PaymentExecutor: transfer result success=" + transfer.isSuccess()
                + " debit=" + netAmount + " " + currency
                + " fromBalancePost=" + transfer.getFromBalance()
                + " toBalancePost=" + transfer.getToBalance());
        if (!transfer.isSuccess()) {
            plugin.getLogger().info("PaymentExecutor: transfer failed, sending not_enough_money");
            MessageUtils.send(from, plugin, "not_enough_money");
            return true;
        }

        String amountWithSymbol = plugin.getCurrencyFormatter().formatPriceForMessage(netAmount, currency);
        String defaultCur = plugin.getDefaultCurrency();
        if (!currency.equalsIgnoreCase(defaultCur)) {
            double equiv = CurrencyUtil.convert(plugin, netAmount, currency, defaultCur);
            if (!Double.isNaN(equiv)) {
                String equivDisplay = plugin.getCurrencyFormatter().formatPriceForMessage(equiv, defaultCur);
                MessageUtils.send(from, plugin, "paid_other_currency", java.util.Map.of("player", toOffline.getName(), "amount", amountWithSymbol, "amount_default", equivDisplay));
            } else {
                MessageUtils.send(from, plugin, "paid", java.util.Map.of("player", toOffline.getName(), "amount", amountWithSymbol));
            }
        } else {
            MessageUtils.send(from, plugin, "paid", java.util.Map.of("player", toOffline.getName(), "amount", amountWithSymbol));
        }
        if (toOffline.isOnline() && toOffline.getPlayer() != null) {
            MessageUtils.send(toOffline.getPlayer(), plugin, "received", java.util.Map.of("player", from.getName(), "amount", amountWithSymbol));
        } else {
            notifyCrossServer(plugin, toOffline.getUniqueId(), toOffline.getName(), from.getName(), amountWithSymbol, currency);
        }
        return true;
    }

    private static void notifyCrossServer(EzEconomyPlugin plugin, UUID recipientUuid,
                                           String recipientName, String senderName,
                                           String amount, String currency) {
        if (!plugin.getConfig().getBoolean("cross-server.enabled", false)) return;
        com.skyblockexp.ezeconomy.messaging.MessagingService ms = plugin.getMessagingService();
        if (ms == null) return;
        ms.sendPaymentNotification(recipientUuid, recipientName, senderName, amount, currency);
        plugin.getLogger().info("PaymentExecutor: sent cross-server notification for " + recipientName);
    }
}
