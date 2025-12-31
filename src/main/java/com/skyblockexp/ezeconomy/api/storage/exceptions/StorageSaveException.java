package com.skyblockexp.ezeconomy.api.storage.exceptions;

public class StorageSaveException extends StorageException {
    public StorageSaveException(String message) {
        super(message);
    }
    public StorageSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
