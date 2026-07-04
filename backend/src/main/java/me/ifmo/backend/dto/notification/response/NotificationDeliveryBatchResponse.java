package me.ifmo.backend.dto.notification.response;

public record NotificationDeliveryBatchResponse(
        int processed,
        int sent,
        int failed,
        int undelivered
) {
}
