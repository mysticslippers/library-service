package me.ifmo.backend.notification.application;

import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import org.jspecify.annotations.NonNull;


public record NotificationDeliveryTask(
        Long id,
        int attemptNumber,
        NotificationChannel channel,
        String recipientEmail,
        String recipientPhone,
        String subject,
        String body
) {
    @Override
    public @NonNull String toString() {
        return "NotificationDeliveryTask[id=" + id + ", attemptNumber=" + attemptNumber + ", channel=" + channel + "]";
    }
}
