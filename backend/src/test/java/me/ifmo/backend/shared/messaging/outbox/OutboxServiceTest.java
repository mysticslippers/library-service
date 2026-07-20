package me.ifmo.backend.shared.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DisplayName("Transactional outbox service")
@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    @Test
    @DisplayName("Stores event metadata and serialized envelope")
    void appendStoresEventMetadataAndSerializedEnvelope() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OutboxService service = new OutboxService(repository, objectMapper);
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-20T08:00:00Z");
        EventEnvelope<Map<String, Long>> event = new EventEnvelope<>(
                eventId,
                "reservation.ready-for-pickup",
                1,
                occurredAt,
                "reservation",
                "10",
                3L,
                "correlation-id",
                Map.of("reservationId", 10L)
        );

        service.append("library.circulation.events", "10", event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent stored = captor.getValue();

        assertThat(stored.getId()).isEqualTo(eventId);
        assertThat(stored.getTopic()).isEqualTo("library.circulation.events");
        assertThat(stored.getEventKey()).isEqualTo("10");
        assertThat(stored.getEventType()).isEqualTo("reservation.ready-for-pickup");
        assertThat(stored.getEventVersion()).isEqualTo(1);
        assertThat(stored.getAggregateType()).isEqualTo("reservation");
        assertThat(stored.getAggregateId()).isEqualTo("10");
        assertThat(stored.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(stored.getAttemptCount()).isZero();
        assertThat(objectMapper.readTree(stored.getEventJson()).get("eventId").asText())
                .isEqualTo(eventId.toString());
        assertThat(objectMapper.readTree(stored.getEventJson()).get("payload").get("reservationId").asLong())
                .isEqualTo(10L);
    }
}
