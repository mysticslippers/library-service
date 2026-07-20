package me.ifmo.backend.notification.integration;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.circulation.integration.event.ReservationReadyForPickupPayload;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.shared.messaging.EventEnvelope;
import me.ifmo.backend.shared.messaging.consumer.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReservationReadyNotificationHandler {

    static final String CONSUMER_NAME = "notification.reservation-ready";

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public boolean handle(
            EventEnvelope<?> event,
            ReservationReadyForPickupPayload payload
    ) {
        int inserted = processedEventRepository.insertIfAbsent(
                event.eventId(),
                CONSUMER_NAME,
                Instant.now()
        );
        if (inserted == 0) {
            meterRegistry.counter(
                    "library.kafka.consumer.events",
                    "event.type", event.eventType(),
                    "result", "duplicate"
            ).increment();
            return false;
        }

        String subject = "Reservation is ready for pickup";
        String body = "Reservation #%d is ready for pickup at branch #%d. Pick it up before %s."
                .formatted(payload.reservationId(), payload.branchId(), payload.expiresAt());

        notificationService.create(new CreateNotificationRequest(
                payload.userId(),
                payload.reservationId(),
                null,
                null,
                NotificationType.RESERVATION_READY,
                null,
                subject,
                body,
                Map.of(
                        "reservationId", payload.reservationId(),
                        "copyId", payload.copyId(),
                        "branchId", payload.branchId(),
                        "expiresAt", payload.expiresAt().toString()
                )
        ));

        meterRegistry.counter(
                "library.kafka.consumer.events",
                "event.type", event.eventType(),
                "result", "processed"
        ).increment();
        return true;
    }
}
