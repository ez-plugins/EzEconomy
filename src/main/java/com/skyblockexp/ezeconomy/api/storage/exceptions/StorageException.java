package com.skyblockexp.ezeconomy.api.storage.exceptions;

public class StorageException extends Exception {
    public StorageException(String message) {
        super(message);
    }
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
