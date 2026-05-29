package com.skyblockexp.ezeconomy.api.storage;

/**
 * Result object for balance and bank balance mutations.
 */
public final class EconomyMutationResult {
    private final boolean success;
    private final double balance;
    private final String failureReason;

    private EconomyMutationResult(boolean success, double balance, String failureReason) {
        this.success = success;
        this.balance = balance;
        this.failureReason = failureReason;
    }

    public static EconomyMutationResult success(double balance) {
        return new EconomyMutationResult(true, balance, null);
    }

    public static EconomyMutationResult failure(double balance, String failureReason) {
        return new EconomyMutationResult(false, balance, failureReason);
    }

    public boolean isSuccess() {
        return success;
    }

    public double getBalance() {
        return balance;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
