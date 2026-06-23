package me.ifmo.backend.exceptions.domain;

import me.ifmo.backend.exceptions.ApiException;
import org.springframework.http.HttpStatus;

public class ResourceInUseException extends ApiException {
    public ResourceInUseException(String message) {
        super(HttpStatus.CONFLICT, "RESOURCE_IN_USE", message);
    }
}
