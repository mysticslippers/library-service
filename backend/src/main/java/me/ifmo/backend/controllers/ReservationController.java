package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.circulation.request.CancelReservationRequest;
import me.ifmo.backend.dto.circulation.request.CreateReservationRequest;
import me.ifmo.backend.dto.circulation.request.ReservationSearchRequest;
import me.ifmo.backend.dto.circulation.response.ReservationResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.services.ReservationService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ReservationResponse getReservationById(@PathVariable Long id) {
        return service.getReservationById(id);
    }

    @PostMapping("/{id}/cancel-by-user")
    public ReservationResponse cancelByUser(@PathVariable Long id, @Valid @RequestBody CancelReservationRequest request) {
        return service.cancelByUser(id, request);
    }

    @PostMapping("/{id}/cancel-by-librarian")
    public ReservationResponse cancelByLibrarian(@PathVariable Long id, @Valid @RequestBody CancelReservationRequest request) {
        return service.cancelByLibrarian(id, request);
    }

    @PostMapping("/{id}/expire")
    public ReservationResponse expire(@PathVariable Long id) {
        return service.expire(id);
    }

    @PostMapping("/{id}/mark-used")
    public ReservationResponse markUsed(@PathVariable Long id) {
        return service.markUsed(id);
    }

    @GetMapping
    public PageResponse<ReservationResponse> search(@Valid @ModelAttribute ReservationSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}