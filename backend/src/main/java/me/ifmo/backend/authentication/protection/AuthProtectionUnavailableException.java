package me.ifmo.backend.authentication.protection;

import me.ifmo.backend.shared.exceptions.ApiException;
import org.springframework.http.HttpStatus;

public class AuthProtectionUnavailableException extends ApiException {
    public AuthProtectionUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_TEMPORARILY_UNAVAILABLE", "Authentication is temporarily unavailable. Try again later.");
    }
}
