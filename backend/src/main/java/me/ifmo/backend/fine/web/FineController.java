package me.ifmo.backend.fine.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.CancelFineRequest;
import me.ifmo.backend.fine.web.request.CreateFineRequest;
import me.ifmo.backend.fine.web.request.FineSearchRequest;
import me.ifmo.backend.fine.web.response.FineResponse;
import me.ifmo.backend.fine.application.FineService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fines")
@RequiredArgsConstructor
@Tag(name = "Fines", description = "Fine creation, cancellation, payment state, and search")
public class FineController {

    private final FineService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Create a fine")
    public FineResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateFineRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a fine by ID")
    public FineResponse getFineById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getFineById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Cancel a fine")
    public FineResponse cancel(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CancelFineRequest request) {
        return service.cancel(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Mark a fine as paid")
    public FineResponse markPaid(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markPaid(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    @Operation(summary = "Search fines")
    public PageResponse<FineResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute FineSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
