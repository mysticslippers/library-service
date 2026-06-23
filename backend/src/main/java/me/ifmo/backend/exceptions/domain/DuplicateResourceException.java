package me.ifmo.backend.exceptions.domain;

import me.ifmo.backend.exceptions.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}
