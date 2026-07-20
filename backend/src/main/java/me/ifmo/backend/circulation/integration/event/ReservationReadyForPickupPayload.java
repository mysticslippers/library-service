package me.ifmo.backend.circulation.integration.event;

import java.time.LocalDateTime;

public record ReservationReadyForPickupPayload(
        Long reservationId,
        Long userId,
        Long copyId,
        Long branchId,
        LocalDateTime expiresAt
) {

    public static final String EVENT_TYPE = "reservation.ready-for-pickup";
    public static final int EVENT_VERSION = 1;
    public static final String AGGREGATE_TYPE = "reservation";
}
