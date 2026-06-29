package me.ifmo.backend.services;

import me.ifmo.backend.dto.fine.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.dto.fine.response.PaymentTransactionResponse;

public interface PaymentTransactionService {

    PaymentTransactionResponse create(CreatePaymentTransactionRequest request);

    PaymentTransactionResponse getPaymentTransactionById(Long id);

    PaymentTransactionResponse getByExternalPayment(String externalPayment);
}
