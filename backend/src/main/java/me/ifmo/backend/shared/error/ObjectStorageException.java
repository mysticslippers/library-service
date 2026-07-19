package me.ifmo.backend.shared.error;

import org.springframework.http.HttpStatus;

public class ObjectStorageException extends ApiException {

    public ObjectStorageException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "OBJECT_STORAGE_UNAVAILABLE", message);
        initCause(cause);
    }
}
