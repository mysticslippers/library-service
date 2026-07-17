package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.shared.error.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Component
public class NotificationChannelPolicy {

    private final EnumSet<NotificationChannel> enabledChannels;

    public NotificationChannelPolicy(
            @Value("${notification.delivery.enabled-channels:EMAIL}") String configuredChannels
    ) {
        this.enabledChannels = parseChannels(configuredChannels);
    }

    public Set<NotificationChannel> enabledChannels() {
        return Set.copyOf(enabledChannels);
    }

    public boolean isEnabled(NotificationChannel channel) {
        return channel != null && enabledChannels.contains(channel);
    }

    public void requireEnabled(NotificationChannel channel) {
        if (!isEnabled(channel))
            throw new BusinessRuleException(
                    "Notification channel '%s' is temporarily unavailable".formatted(channel));
    }

    public NotificationChannel defaultChannel() {
        if (enabledChannels.contains(NotificationChannel.EMAIL))
            return NotificationChannel.EMAIL;

        return enabledChannels.iterator().next();
    }

    private EnumSet<NotificationChannel> parseChannels(String configuredChannels) {
        if (configuredChannels == null || configuredChannels.isBlank())
            throw new IllegalStateException("At least one notification delivery channel must be enabled");

        try {
            EnumSet<NotificationChannel> channels = Arrays.stream(configuredChannels.split(","))
                    .map(String::strip)
                    .filter(value -> !value.isBlank())
                    .map(value -> NotificationChannel.valueOf(value.toUpperCase(Locale.ROOT)))
                    .collect(
                            () -> EnumSet.noneOf(NotificationChannel.class),
                            EnumSet::add,
                            EnumSet::addAll
                    );

            if (channels.isEmpty())
                throw new IllegalStateException("At least one notification delivery channel must be enabled");

            return channels;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Unknown notification delivery channel in '%s'".formatted(configuredChannels), exception);
        }
    }
}
