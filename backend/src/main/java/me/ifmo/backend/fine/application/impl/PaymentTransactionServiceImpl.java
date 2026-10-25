package me.ifmo.backend.fine.application.impl;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import me.ifmo.backend.fine.application.PaymentLocks;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.CreatePaymentTransactionRequest;
import me.ifmo.backend.fine.web.request.PaymentTransactionSearchRequest;
import me.ifmo.backend.fine.web.request.UpdatePaymentStatusRequest;
import me.ifmo.backend.fine.web.response.PaymentTransactionResponse;
import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.domain.PaymentTransaction;
import me.ifmo.backend.fine.domain.enums.FineStatus;
import me.ifmo.backend.fine.domain.enums.PaymentStatus;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.DuplicateResourceException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import me.ifmo.backend.fine.mapper.PaymentTransactionMapper;
import me.ifmo.backend.fine.persistence.PaymentTransactionRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import me.ifmo.backend.fine.application.PaymentTransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private static final Set<PaymentStatus> FINAL_STATUSES =
            Set.of(PaymentStatus.SUCCESS, PaymentStatus.DECLINED, PaymentStatus.CANCELLED,
                    PaymentStatus.FAILED, PaymentStatus.TIMEOUT);
    private static final Set<PaymentStatus> IN_PROGRESS_STATUSES =
            Set.of(PaymentStatus.CREATED, PaymentStatus.PENDING);

    private final PaymentTransactionRepository repository;
    private final UserRoleRepository userRoleRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentLocks paymentLocks;
    private final EntityManager entityManager;

    private String normalize(String value) {
        if (value == null || value.strip().isBlank()) {
            return null;
        }

        return value.strip();
    }

    private boolean isStaff(Long actorUserId) {
        return userRoleRepository.findRoleCodesByUser_Id(actorUserId).stream()
                .noneMatch(role -> role == RoleCode.LIBRARIAN || role == RoleCode.ADMIN);
    }

    private void validateStaff(Long actorUserId) {
        if (isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validateOwnerOrStaff(Fine fine, Long actorUserId) {
        if (!fine.getUser().getId().equals(actorUserId) && isStaff(actorUserId))
            throw new AccessDeniedException("Access is denied");
    }

    private void validate(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessRuleException("Payment amount must be positive");
    }

    private boolean isTransitionAllowed(PaymentStatus current, PaymentStatus target) {
        if (current == target)
            return true;

        if (FINAL_STATUSES.contains(current))
            return false;

        return switch (current) {
            case CREATED -> target == PaymentStatus.PENDING
                    || target == PaymentStatus.SUCCESS
                    || target == PaymentStatus.CANCELLED
                    || target == PaymentStatus.FAILED;
            case PENDING -> target == PaymentStatus.SUCCESS
                    || target == PaymentStatus.DECLINED
                    || target == PaymentStatus.CANCELLED
                    || target == PaymentStatus.FAILED
                    || target == PaymentStatus.TIMEOUT;
            case SUCCESS, DECLINED, CANCELLED, FAILED, TIMEOUT -> false;
        };
    }

    @Override
    @Transactional
    public PaymentTransactionResponse create(Long actorUserId, CreatePaymentTransactionRequest request) {
        Fine fine = paymentLocks.fine(request.fineId());
        validateOwnerOrStaff(fine, actorUserId);

        if (fine.getStatus() != FineStatus.ACTIVE)
            throw new BusinessRuleException("Payment can be created only for active fine");

        validate(request.amount());

        if (request.amount().compareTo(fine.getAmount()) != 0)
            throw new BusinessRuleException("Payment amount must be equal to fine amount");

        String externalPayment = normalize(request.externalPayment());

        if (externalPayment != null && repository.existsByExternalPayment(externalPayment))
            throw new DuplicateResourceException("Payment transaction with external payment id '%s' already exists".formatted(externalPayment));

        if (repository.existsByFine_IdAndStatusIn(fine.getId(), IN_PROGRESS_STATUSES))
            throw new BusinessRuleException("Fine already has payment transaction in progress");

        if (repository.existsByFine_IdAndStatus(fine.getId(), PaymentStatus.SUCCESS))
            throw new BusinessRuleException("Fine already has successful payment transaction");

        PaymentTransaction transaction = paymentTransactionMapper.toEntity(fine, externalPayment, request.amount());
        transaction.setStatus(PaymentStatus.CREATED);

        PaymentTransaction saved = repository.saveAndFlush(transaction);
        entityManager.refresh(saved);
        return paymentTransactionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionResponse getPaymentTransactionById(Long actorUserId, Long id) {
        PaymentTransaction transaction = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Payment transaction with id '%s' not found".formatted(id)));
        validateOwnerOrStaff(transaction.getFine(), actorUserId);

        return paymentTransactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransactionResponse getByExternalPayment(Long actorUserId, String externalPayment) {
        String normalizedExternalPayment = normalize(externalPayment);

        if (normalizedExternalPayment == null)
            throw new BusinessRuleException("External payment id must not be blank");

        PaymentTransaction transaction = repository.findByExternalPayment(normalizedExternalPayment).orElseThrow(
                () -> new ResourceNotFoundException(
                        "Payment transaction with external payment id '%s' not found".formatted(normalizedExternalPayment)));
        validateOwnerOrStaff(transaction.getFine(), actorUserId);

        return paymentTransactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional
    public PaymentTransactionResponse updateStatus(Long actorUserId, Long id, UpdatePaymentStatusRequest request) {
        validateStaff(actorUserId);

        PaymentTransaction transaction = paymentLocks.payment(id);
        Fine fine = transaction.getFine();
        PaymentStatus targetStatus = request.status();

        if (targetStatus == null)
            throw new BusinessRuleException("Payment status must be specified");

        String externalPayment = normalize(request.externalPayment());
        if (externalPayment == null)
            externalPayment = transaction.getExternalPayment();

        if (transaction.getExternalPayment() != null && !transaction.getExternalPayment().equals(externalPayment))
            throw new BusinessRuleException("External payment id cannot be changed once assigned");

        if (transaction.getStatus() == targetStatus && Objects.equals(transaction.getExternalPayment(), externalPayment)) {
            if (targetStatus == PaymentStatus.SUCCESS && fine.getStatus() != FineStatus.PAID)
                throw new BusinessRuleException("Successful payment must have a paid fine");
            return paymentTransactionMapper.toResponse(transaction);
        }

        if (!isTransitionAllowed(transaction.getStatus(), targetStatus))
            throw new BusinessRuleException("Payment status transition from '%s' to '%s' is not allowed".formatted(transaction.getStatus(), targetStatus));

        if (FINAL_STATUSES.contains(transaction.getStatus()))
            throw new BusinessRuleException("Final payment transaction cannot be changed");

        if (fine.getStatus() != FineStatus.ACTIVE)
            throw new BusinessRuleException("Payment can be updated only for active fine");

        if (transaction.getAmount().compareTo(fine.getAmount()) != 0)
            throw new BusinessRuleException("Payment amount must be equal to fine amount");

        if (externalPayment != null && !externalPayment.equals(transaction.getExternalPayment()) && repository.existsByExternalPayment(externalPayment))
            throw new DuplicateResourceException(
                    "Payment transaction with external payment id '%s' already exists".formatted(externalPayment));

        paymentTransactionMapper.updateStatus(new UpdatePaymentStatusRequest(targetStatus, externalPayment), transaction);
        PaymentTransaction saved = repository.saveAndFlush(transaction);
        entityManager.refresh(saved);
        entityManager.refresh(fine);
        return paymentTransactionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentTransactionResponse> search(Long actorUserId, PaymentTransactionSearchRequest request, Pageable pageable) {
        Long userId = null;

        if (isStaff(actorUserId))
            userId = actorUserId;

        Long filterUserId = userId;
        Specification<PaymentTransaction> specification =
                (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (request.fineId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("fine").get("id"), request.fineId()));
        if (filterUserId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("fine").get("user").get("id"), filterUserId));
        if (request.status() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), request.status()));
        if (request.createdFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
        if (request.createdTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));

        Page<PaymentTransaction> transactions = repository.findAll(specification, pageable);

        Page<PaymentTransactionResponse> responses = transactions.map(paymentTransactionMapper::toResponse);

        return PageResponse.from(responses);
    }
}
