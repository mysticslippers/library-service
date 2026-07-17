package me.ifmo.backend.fine.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.fine.web.request.PaymentTransactionSearchRequest;
import me.ifmo.backend.fine.web.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.fine.web.response.PaymentTransactionResponse;
import org.springframework.data.domain.Pageable;

public interface PaymentTransactionService {

    PaymentTransactionResponse create(Long actorUserId, CreatePaymentTransactionRequest request);

    PaymentTransactionResponse getPaymentTransactionById(Long actorUserId, Long id);

    PaymentTransactionResponse getByExternalPayment(Long actorUserId, String externalPayment);

    PaymentTransactionResponse updateStatus(Long actorUserId, Long id, UpdatePaymentStatusRequest request);

    PageResponse<PaymentTransactionResponse> search(Long actorUserId, PaymentTransactionSearchRequest request, Pageable pageable);
}
