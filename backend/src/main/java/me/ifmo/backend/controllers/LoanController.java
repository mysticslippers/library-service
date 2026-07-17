package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.request.LoanSearchRequest;
import me.ifmo.backend.dto.circulation.request.RenewLoanRequest;
import me.ifmo.backend.dto.circulation.request.ReturnLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.services.LoanService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public LoanResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateLoanRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public LoanResponse getLoanById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getLoanById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public LoanResponse returnLoan(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody ReturnLoanRequest request) {
        return service.returnLoan(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/renew")
    public LoanResponse renew(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody RenewLoanRequest request) {
        return service.renew(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/mark-overdue")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public LoanResponse markOverdue(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markOverdue(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/mark-lost")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public LoanResponse markLost(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markLost(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    public PageResponse<LoanResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute LoanSearchRequest request, Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
