package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;

public interface ReservationService {

    ReservationResponse create(CreateReservationRequest request);

    ReservationResponse getReservationById(Long id);

    ReservationResponse cancelByUser(Long id, CancelReservationRequest request);

    ReservationResponse cancelByLibrarian(Long id, CancelReservationRequest request);
}
