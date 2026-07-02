package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.request.LoanSearchRequest;
import me.ifmo.backend.dto.circulation.request.RenewLoanRequest;
import me.ifmo.backend.dto.circulation.request.ReturnLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.services.LoanService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse create(@Valid @RequestBody CreateLoanRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public LoanResponse getLoanById(@PathVariable Long id) {
        return service.getLoanById(id);
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@PathVariable Long id, @Valid @RequestBody ReturnLoanRequest request) {
        return service.returnLoan(id, request);
    }

    @PostMapping("/{id}/renew")
    public LoanResponse renew(@PathVariable Long id, @Valid @RequestBody RenewLoanRequest request) {
        return service.renew(id, request);
    }

    @PostMapping("/{id}/mark-overdue")
    public LoanResponse markOverdue(@PathVariable Long id) {
        return service.markOverdue(id);
    }

    @PostMapping("/{id}/mark-lost")
    public LoanResponse markLost(@PathVariable Long id) {
        return service.markLost(id);
    }

    @GetMapping
    public PageResponse<LoanResponse> search(@Valid @ModelAttribute LoanSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}
