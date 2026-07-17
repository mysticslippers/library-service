package me.ifmo.backend.notification.web.response;

public record NotificationDeliveryBatchResponse(
        int processed,
        int sent,
        int failed,
        int undelivered
) {
}
