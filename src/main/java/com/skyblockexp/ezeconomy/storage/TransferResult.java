package com.skyblockexp.ezeconomy.storage;

public final class TransferResult {
    private final boolean success;
    private final double fromBalance;
    private final double toBalance;

    private TransferResult(boolean success, double fromBalance, double toBalance) {
        this.success = success;
        this.fromBalance = fromBalance;
        this.toBalance = toBalance;
    }

    public static TransferResult success(double fromBalance, double toBalance) {
        return new TransferResult(true, fromBalance, toBalance);
    }

    public static TransferResult failure(double fromBalance, double toBalance) {
        return new TransferResult(false, fromBalance, toBalance);
    }

    public boolean isSuccess() {
        return success;
    }

    public double getFromBalance() {
        return fromBalance;
    }

    public double getToBalance() {
        return toBalance;
    }
}
