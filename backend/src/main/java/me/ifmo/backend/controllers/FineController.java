package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.request.FineSearchRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;
import me.ifmo.backend.services.FineService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public FineResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateFineRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public FineResponse getFineById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getFineById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public FineResponse cancel(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CancelFineRequest request) {
        return service.cancel(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public FineResponse markPaid(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markPaid(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    public PageResponse<FineResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute FineSearchRequest request, Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}