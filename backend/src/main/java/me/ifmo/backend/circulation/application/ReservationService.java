package me.ifmo.backend.circulation.application;

import me.ifmo.backend.circulation.web.request.CancelReservationRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.circulation.web.request.ReservationSearchRequest;
import me.ifmo.backend.circulation.web.response.ReservationResponse;
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
