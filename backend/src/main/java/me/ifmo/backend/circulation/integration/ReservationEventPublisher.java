package me.ifmo.backend.circulation.integration;

import me.ifmo.backend.circulation.domain.Reservation;

public interface ReservationEventPublisher {

    void reservationReadyForPickup(Long actorUserId, Reservation reservation);
}
