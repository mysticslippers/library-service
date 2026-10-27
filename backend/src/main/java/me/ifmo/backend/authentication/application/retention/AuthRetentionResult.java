package me.ifmo.backend.authentication.application.retention;

public record AuthRetentionResult(
        int quotaBuckets,
        int secretNotifications,
        int tokens
) {
}
