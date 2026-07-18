package me.ifmo.backend.circulation.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.circulation.web.request.CancelReservationRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.circulation.web.request.ReservationSearchRequest;
import me.ifmo.backend.circulation.web.response.ReservationResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.circulation.application.ReservationService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Material reservation and pickup lifecycle")
public class ReservationController {

    private final ReservationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a reservation")
    public ReservationResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateReservationRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation by ID")
    public ReservationResponse getReservationById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getReservationById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/cancel-by-user")
    @Operation(summary = "Cancel a reservation as its owner")
    public ReservationResponse cancelByUser(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CancelReservationRequest request) {
        return service.cancelByUser(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/cancel-by-librarian")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Cancel a reservation as a librarian")
    public ReservationResponse cancelByLibrarian(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CancelReservationRequest request) {
        return service.cancelByLibrarian(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Mark a reservation as expired")
    public ReservationResponse expire(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.expire(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Mark a reservation as ready for pickup")
    public ReservationResponse markReadyForPickup(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markReadyForPickup(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/mark-used")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Mark a reservation as used")
    public ReservationResponse markUsed(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markUsed(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    @Operation(summary = "Search reservations")
    public PageResponse<ReservationResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute ReservationSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
