package me.ifmo.backend.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceInUseException extends ApiException {
    public ResourceInUseException(String message) {
        super(HttpStatus.CONFLICT, "RESOURCE_IN_USE", message);
    }
}
