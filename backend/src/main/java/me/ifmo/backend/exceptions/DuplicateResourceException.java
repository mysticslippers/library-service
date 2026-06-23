package me.ifmo.backend.exceptions;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}
