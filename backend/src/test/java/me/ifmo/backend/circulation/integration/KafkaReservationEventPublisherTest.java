package me.ifmo.backend.circulation.integration;

import me.ifmo.backend.catalog.domain.MaterialCopy;
import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.library.domain.Branch;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import me.ifmo.backend.shared.messaging.KafkaMessagingProperties;
import me.ifmo.backend.shared.messaging.outbox.OutboxService;
import me.ifmo.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DisplayName("Kafka reservation event publisher")
@ExtendWith(MockitoExtension.class)
class KafkaReservationEventPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Test
    @DisplayName("Appends versioned ready-for-pickup event using reservation ID as key")
    void readyForPickupAppendsVersionedEventUsingReservationIdAsKey() {
        KafkaMessagingProperties properties = properties();
        KafkaReservationEventPublisher publisher =
                new KafkaReservationEventPublisher(outboxService, properties);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        Branch branch = Branch.builder().id(20L).build();
        Reservation reservation = Reservation.builder()
                .id(10L)
                .user(User.builder().id(30L).build())
                .copy(MaterialCopy.builder().id(40L).branch(branch).build())
                .branch(branch)
                .expiresAt(expiresAt)
                .build();

        publisher.reservationReadyForPickup(50L, reservation);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxService).append(
                eq("library.circulation.events"),
                eq("10"),
                captor.capture()
        );

        EventEnvelope<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(ReservationReadyForPickupPayload.EVENT_TYPE);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateType()).isEqualTo("reservation");
        assertThat(event.aggregateId()).isEqualTo("10");
        assertThat(event.actorUserId()).isEqualTo(50L);
        assertThat(event.payload()).isEqualTo(new ReservationReadyForPickupPayload(
                10L, 30L, 40L, 20L, expiresAt
        ));
    }

    private KafkaMessagingProperties properties() {
        return new KafkaMessagingProperties(
                new KafkaMessagingProperties.Topics("library.circulation.events"),
                new KafkaMessagingProperties.TopicSettings(3, (short) 1, 604_800_000L),
                new KafkaMessagingProperties.Notification("library-notification", 4, 1000),
                new KafkaMessagingProperties.Outbox(
                        50, 1000, Duration.ofSeconds(5), Duration.ofSeconds(5)
                )
        );
    }
}
