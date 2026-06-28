package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.request.ReservationSearchRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ReservationService {

    ReservationResponse create(CreateReservationRequest request);

    ReservationResponse getReservationById(Long id);

    ReservationResponse cancelByUser(Long id, CancelReservationRequest request);

    ReservationResponse cancelByLibrarian(Long id, CancelReservationRequest request);

    ReservationResponse expire(Long id);

    ReservationResponse markUsed(Long id);

    PageResponse<ReservationResponse> search(ReservationSearchRequest request, Pageable pageable);
}
