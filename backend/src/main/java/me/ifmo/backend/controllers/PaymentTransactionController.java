package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.dto.fine.request.PaymentTransactionSearchRequest;
import me.ifmo.backend.dto.fine.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.dto.fine.response.PaymentTransactionResponse;
import me.ifmo.backend.services.PaymentTransactionService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentTransactionResponse create(@Valid @RequestBody CreatePaymentTransactionRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public PaymentTransactionResponse getPaymentTransactionById(@PathVariable Long id) {
        return service.getPaymentTransactionById(id);
    }

    @GetMapping("/external/{externalPayment}")
    public PaymentTransactionResponse getByExternalPayment(@PathVariable String externalPayment) {
        return service.getByExternalPayment(externalPayment);
    }

    @PatchMapping("/{id}/status")
    public PaymentTransactionResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @GetMapping
    public PageResponse<PaymentTransactionResponse> search(@Valid @ModelAttribute PaymentTransactionSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}