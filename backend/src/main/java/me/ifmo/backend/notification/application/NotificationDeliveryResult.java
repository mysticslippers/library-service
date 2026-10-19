package me.ifmo.backend.notification.application;

public record NotificationDeliveryResult(
        Outcome outcome,
        String externalMessageId,
        String errorMessage
) {
    public enum Outcome { SUCCESS, FAILURE, UNKNOWN }

    public boolean success() {
        return outcome == Outcome.SUCCESS;
    }

    public static NotificationDeliveryResult sent(String messageId) {
        return new NotificationDeliveryResult(Outcome.SUCCESS, messageId, null);
    }

    public static NotificationDeliveryResult failed(String error) {
        return new NotificationDeliveryResult(Outcome.FAILURE, null, error);
    }

    public static NotificationDeliveryResult unknown(String messageId, String error) {
        return new NotificationDeliveryResult(Outcome.UNKNOWN, messageId, error);
    }
}
