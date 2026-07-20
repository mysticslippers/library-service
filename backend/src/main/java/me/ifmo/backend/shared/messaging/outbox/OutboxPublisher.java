package me.ifmo.backend.shared.messaging.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int MAX_STORED_ERROR_LENGTH = 4000;

    private final OutboxEventRepository repository;
    private final OutboxDeliveryService deliveryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMessagingProperties properties;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${messaging.kafka.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        repository.findReadyForPublishing(
                        Instant.now(),
                        PageRequest.of(0, properties.outbox().batchSize())
                )
                .forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getEventJson())
                    .get(properties.outbox().publishTimeout().toMillis(), TimeUnit.MILLISECONDS);

            deliveryService.markPublished(event.getId(), Instant.now());
            meterRegistry.counter(
                    "library.kafka.outbox.events",
                    "event.type", event.getEventType(),
                    "result", "published"
            ).increment();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(event, exception);
        } catch (Exception exception) {
            recordFailure(event, exception);
        }
    }

    private void recordFailure(OutboxEvent event, Exception exception) {
        String error = "%s: %s".formatted(
                exception.getClass().getSimpleName(),
                exception.getMessage() != null ? exception.getMessage() : "unknown error"
        );
        if (error.length() > MAX_STORED_ERROR_LENGTH)
            error = error.substring(0, MAX_STORED_ERROR_LENGTH);

        deliveryService.markFailed(event.getId(), error, Instant.now());
        meterRegistry.counter(
                "library.kafka.outbox.events",
                "event.type", event.getEventType(),
                "result", "failed"
        ).increment();
        log.warn("Failed to publish outbox event id={}, type={}: {}",
                event.getId(), event.getEventType(), error);
    }
}
