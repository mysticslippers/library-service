package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.dto.fine.request.PaymentTransactionSearchRequest;
import me.ifmo.backend.dto.fine.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.dto.fine.response.PaymentTransactionResponse;
import me.ifmo.backend.services.PaymentTransactionService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentTransactionResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreatePaymentTransactionRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    public PaymentTransactionResponse getPaymentTransactionById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getPaymentTransactionById(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping("/external/{externalPayment}")
    public PaymentTransactionResponse getByExternalPayment(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String externalPayment) {
        return service.getByExternalPayment(Long.valueOf(userDetails.getUsername()), externalPayment);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public PaymentTransactionResponse updateStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return service.updateStatus(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @GetMapping
    public PageResponse<PaymentTransactionResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute PaymentTransactionSearchRequest request, Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}