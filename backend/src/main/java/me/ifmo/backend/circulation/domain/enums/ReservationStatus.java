package me.ifmo.backend.circulation.domain.enums;

public enum ReservationStatus {
    ACTIVE,
    READY_FOR_PICKUP,
    CANCELLED_BY_USER,
    CANCELLED_BY_LIBRARIAN,
    EXPIRED,
    USED
}
