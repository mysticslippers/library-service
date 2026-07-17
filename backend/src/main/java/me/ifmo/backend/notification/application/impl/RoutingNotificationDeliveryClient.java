package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.notification.application.NotificationChannelSender;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.notification.application.NotificationDeliveryResult;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutingNotificationDeliveryClient implements NotificationDeliveryClient {

    private final Map<NotificationChannel, NotificationChannelSender> senders;

    public RoutingNotificationDeliveryClient(List<NotificationChannelSender> channelSenders) {
        this.senders = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelSender sender : channelSenders) {
            NotificationChannelSender previous = senders.put(sender.channel(), sender);
            if (previous != null)
                throw new IllegalStateException(
                        "Multiple notification senders are configured for channel '%s'".formatted(sender.channel()));
        }
    }

    @Override
    public NotificationDeliveryResult send(Notification notification) {
        if (notification == null || notification.getChannel() == null)
            return failure("Notification channel must not be null");

        NotificationChannelSender sender = senders.get(notification.getChannel());
        if (sender == null)
            return failure("Notification channel '%s' is not configured".formatted(notification.getChannel()));

        return sender.send(notification);
    }

    private NotificationDeliveryResult failure(String errorMessage) {
        return new NotificationDeliveryResult(false, null, errorMessage);
    }
}
