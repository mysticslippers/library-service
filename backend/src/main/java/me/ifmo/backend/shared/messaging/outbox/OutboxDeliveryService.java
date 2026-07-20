package me.ifmo.backend.shared.messaging.outbox;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxDeliveryService {

    private static final int MAX_RETRY_MULTIPLIER_POWER = 6;

    private final OutboxEventRepository repository;
    private final KafkaMessagingProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, Instant publishedAt) {
        repository.findById(eventId).ifPresent(event -> {
            event.setPublishedAt(publishedAt);
            event.setNextAttemptAt(null);
            event.setLastError(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String error, Instant failedAt) {
        repository.findById(eventId).ifPresent(event -> {
            int attemptCount = event.getAttemptCount() + 1;
            long multiplier = 1L << Math.min(attemptCount - 1, MAX_RETRY_MULTIPLIER_POWER);
            Duration delay = properties.outbox().retryDelay().multipliedBy(multiplier);

            event.setAttemptCount(attemptCount);
            event.setNextAttemptAt(failedAt.plus(delay));
            event.setLastError(error);
        });
    }
}
