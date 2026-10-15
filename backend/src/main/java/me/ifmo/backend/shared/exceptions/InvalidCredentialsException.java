package me.ifmo.backend.shared.exceptions;

public class InvalidCredentialsException extends BusinessRuleException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
