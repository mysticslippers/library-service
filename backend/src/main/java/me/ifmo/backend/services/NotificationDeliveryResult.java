package me.ifmo.backend.services;

public record NotificationDeliveryResult(
        boolean success,
        String externalMessageId,
        String errorMessage
) {
}
