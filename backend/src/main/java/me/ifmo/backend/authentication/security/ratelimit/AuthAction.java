package me.ifmo.backend.authentication.security.ratelimit;

public enum AuthAction {
    LOGIN,
    REGISTER,
    RECOVERY,
    RESEND,
    ACTIVATE,
    RESET
}
