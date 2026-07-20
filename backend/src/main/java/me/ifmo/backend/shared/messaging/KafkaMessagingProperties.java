package me.ifmo.backend.shared.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "messaging.kafka")
public record KafkaMessagingProperties(
        Topics topics,
        TopicSettings topic,
        Notification notification,
        Outbox outbox
) {

    public record Topics(String circulation) {
    }

    public record TopicSettings(int partitions, short replicationFactor, long retentionMs) {
    }

    public record Notification(String groupId, int retryAttempts, long retryDelayMs) {
    }

    public record Outbox(int batchSize, long pollIntervalMs, Duration publishTimeout, Duration retryDelay) {
    }
}
