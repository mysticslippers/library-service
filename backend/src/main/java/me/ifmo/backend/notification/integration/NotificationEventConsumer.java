package me.ifmo.backend.notification.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final TypeReference<EventEnvelope<JsonNode>> EVENT_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final ReservationReadyNotificationHandler reservationReadyHandler;
    private final MeterRegistry meterRegistry;

    @RetryableTopic(
            attempts = "${messaging.kafka.notification.retry-attempts:4}",
            backoff = @Backoff(
                    delayExpression = "${messaging.kafka.notification.retry-delay-ms:1000}",
                    multiplier = 2.0
            ),
            autoCreateTopics = "true",
            numPartitions = "${messaging.kafka.topic.partitions:3}",
            replicationFactor = "${messaging.kafka.topic.replication-factor:1}"
    )
    @KafkaListener(
            topics = "${messaging.kafka.topics.circulation}",
            groupId = "${messaging.kafka.notification.group-id}"
    )
    public void consume(String message) throws JsonProcessingException {
        EventEnvelope<JsonNode> event = objectMapper.readValue(message, EVENT_TYPE);

        if (!ReservationReadyForPickupPayload.EVENT_TYPE.equals(event.eventType())) {
            log.debug("Notification consumer ignored unsupported event type '{}'", event.eventType());
            meterRegistry.counter(
                    "library.kafka.consumer.events",
                    "event.type", event.eventType(),
                    "result", "ignored"
            ).increment();
            return;
        }

        if (event.eventVersion() != ReservationReadyForPickupPayload.EVENT_VERSION)
            throw new IllegalArgumentException(
                    "Unsupported version '%s' for event '%s'"
                            .formatted(event.eventVersion(), event.eventType())
            );

        ReservationReadyForPickupPayload payload = objectMapper.treeToValue(
                event.payload(),
                ReservationReadyForPickupPayload.class
        );
        reservationReadyHandler.handle(event, payload);
    }

    @DltHandler
    public void handleDeadLetter(String message) {
        meterRegistry.counter(
                "library.kafka.consumer.events",
                "event.type", ReservationReadyForPickupPayload.EVENT_TYPE,
                "result", "dead-letter"
        ).increment();
        log.error("Kafka notification event moved to DLT after retry exhaustion; payloadSize={}",
                message.length());
    }
}
