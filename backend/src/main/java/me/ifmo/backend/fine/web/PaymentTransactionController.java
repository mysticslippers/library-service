package me.ifmo.backend.fine.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.fine.web.request.PaymentTransactionSearchRequest;
import me.ifmo.backend.fine.web.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.fine.web.response.PaymentTransactionResponse;
import me.ifmo.backend.fine.application.PaymentTransactionService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment-transactions")
@RequiredArgsConstructor
@Tag(name = "Payment Transactions", description = "Fine payment transactions and their processing status")
public class PaymentTransactionController {

    private final PaymentTransactionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a payment transaction")
    public PaymentTransactionResponse create(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreatePaymentTransactionRequest request) {
        return service.create(Long.valueOf(userDetails.getUsername()), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment transaction by ID")
    public PaymentTransactionResponse getPaymentTransactionById(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return service.getPaymentTransactionById(Long.valueOf(userDetails.getUsername()), id);
    }

    @GetMapping("/external/{externalPayment}")
    @Operation(summary = "Get a transaction by external payment ID")
    public PaymentTransactionResponse getByExternalPayment(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String externalPayment) {
        return service.getByExternalPayment(Long.valueOf(userDetails.getUsername()), externalPayment);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Update a payment transaction's status")
    public PaymentTransactionResponse updateStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return service.updateStatus(Long.valueOf(userDetails.getUsername()), id, request);
    }

    @GetMapping
    @Operation(summary = "Search payment transactions")
    public PageResponse<PaymentTransactionResponse> search(@AuthenticationPrincipal UserDetails userDetails, @Valid @ModelAttribute PaymentTransactionSearchRequest request, @ParameterObject Pageable pageable) {
        return service.search(Long.valueOf(userDetails.getUsername()), request, pageable);
    }
}
