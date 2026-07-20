package me.ifmo.backend.shared.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String eventKey, EventEnvelope<?> event) {
        repository.save(OutboxEvent.builder()
                .id(event.eventId())
                .topic(topic)
                .eventKey(eventKey)
                .eventType(event.eventType())
                .eventVersion(event.eventVersion())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .eventJson(serialize(event))
                .occurredAt(event.occurredAt())
                .createdAt(Instant.now())
                .attemptCount(0)
                .build());
    }

    private String serialize(EventEnvelope<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event '%s'".formatted(event.eventType()), exception);
        }
    }
}
