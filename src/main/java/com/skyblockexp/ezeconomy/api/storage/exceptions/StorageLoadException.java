package com.skyblockexp.ezeconomy.api.storage.exceptions;

public class StorageLoadException extends StorageException {
    public StorageLoadException(String message) {
        super(message);
    }
    public StorageLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
