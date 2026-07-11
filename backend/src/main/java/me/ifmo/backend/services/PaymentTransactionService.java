package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.dto.fine.request.PaymentTransactionSearchRequest;
import me.ifmo.backend.dto.fine.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.dto.fine.response.PaymentTransactionResponse;
import org.springframework.data.domain.Pageable;

public interface PaymentTransactionService {

    PaymentTransactionResponse create(Long actorUserId, CreatePaymentTransactionRequest request);

    PaymentTransactionResponse getPaymentTransactionById(Long actorUserId, Long id);

    PaymentTransactionResponse getByExternalPayment(Long actorUserId, String externalPayment);

    PaymentTransactionResponse updateStatus(Long actorUserId, Long id, UpdatePaymentStatusRequest request);

    PageResponse<PaymentTransactionResponse> search(Long actorUserId, PaymentTransactionSearchRequest request, Pageable pageable);
}
