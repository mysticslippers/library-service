package me.ifmo.backend.shared.messaging.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxDeliveryService {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMessagingProperties properties;
    private final MeterRegistry meterRegistry;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishNext() {
        var pending = repository.findNextReadyForPublishing();
        if (pending.isEmpty())
            return false;

        OutboxEvent event = pending.get();
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getEventJson()).get(properties.outbox().publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(event, exception);
            return true;
        } catch (Exception exception) {
            recordFailure(event, exception);
            return true;
        }

        event.setPublishedAt(Instant.now());
        event.setNextAttemptAt(null);
        event.setLastError(null);
        meterRegistry.counter("library.kafka.outbox.events", "event.type", event.getEventType(), "result", "published").increment();
        return true;
    }

    private void recordFailure(OutboxEvent event, Exception exception) {
        int attemptCount = Math.min(event.getAttemptCount(), 1000000) + 1;
        long multiplier = 1L << Math.min(attemptCount - 1, 6);

        event.setAttemptCount(attemptCount);
        event.setNextAttemptAt(Instant.now().plus(properties.outbox().retryDelay().multipliedBy(multiplier)));
        event.setLastError(exception.getClass().getSimpleName());

        meterRegistry.counter("library.kafka.outbox.events", "event.type", event.getEventType(), "result", "failed").increment();
        log.warn("Outbox publication failed; event_id={} error_type={}",
                event.getId(), exception.getClass().getSimpleName());
    }
}
