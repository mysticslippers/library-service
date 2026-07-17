package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.request.ReservationSearchRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ReservationService {

    ReservationResponse create(Long actorUserId, CreateReservationRequest request);

    ReservationResponse getReservationById(Long actorUserId, Long id);

    ReservationResponse cancelByUser(Long actorUserId, Long id, CancelReservationRequest request);

    ReservationResponse cancelByLibrarian(Long actorUserId, Long id, CancelReservationRequest request);

    ReservationResponse expire(Long actorUserId, Long id);

    ReservationResponse markReadyForPickup(Long actorUserId, Long id);

    ReservationResponse markUsed(Long actorUserId, Long id);

    PageResponse<ReservationResponse> search(Long actorUserId, ReservationSearchRequest request, Pageable pageable);
}
