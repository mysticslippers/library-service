package me.ifmo.backend.authentication.security.ratelimit;

public record AuthRateLimitDecision(
        boolean allowed,
        long retryAfter
) {
}
