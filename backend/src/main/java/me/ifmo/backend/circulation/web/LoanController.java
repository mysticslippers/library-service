package me.ifmo.backend.circulation.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.LoanSearchRequest;
import me.ifmo.backend.circulation.web.request.RenewLoanRequest;
import me.ifmo.backend.circulation.web.request.ReturnLoanRequest;
import me.ifmo.backend.circulation.web.response.LoanResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.circulation.application.LoanService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Material lending, renewal, return, and overdue workflows")
public class LoanController {

    private final LoanService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Create a loan")
    public LoanResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateLoanRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a loan by ID")
    public LoanResponse getLoanById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getLoanById(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Return a loaned material")
    public LoanResponse returnLoan(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody ReturnLoanRequest request) {
        return service.returnLoan(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renew a loan")
    public LoanResponse renew(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody RenewLoanRequest request) {
        return service.renew(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @PostMapping("/{id}/mark-overdue")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Mark a loan as overdue")
    public LoanResponse markOverdue(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markOverdue(Long.valueOf(userDetails.getUsername()), id);
    }

    @PostMapping("/{id}/mark-lost")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Mark a loaned material as lost")
    public LoanResponse markLost(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.markLost(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping
    @Operation(summary = "Search loans")
    public PageResponse<LoanResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute LoanSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
