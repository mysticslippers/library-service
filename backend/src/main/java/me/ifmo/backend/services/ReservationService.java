package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;

public interface ReservationService {

    ReservationResponse create(CreateReservationRequest request);
}
