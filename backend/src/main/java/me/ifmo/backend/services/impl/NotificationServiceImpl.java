package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.NotificationMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Set<NotificationStatus> FINAL_STATUSES =
            Set.of(NotificationStatus.DELIVERED, NotificationStatus.CANCELLED);

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final NotificationMapper mapper;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Subject")) {
            if (value == null || value.strip().isBlank())
                return null;
        } else {
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));
        }
        return value.strip();
    }

    private boolean isTransitionAllowed(NotificationStatus current, NotificationStatus target) {
        if (current == target)
            return true;

        if (FINAL_STATUSES.contains(current))
            return false;

        return switch (current) {
            case PLANNED, FAILED -> target == NotificationStatus.PENDING || target == NotificationStatus.CANCELLED;
            case PENDING -> target == NotificationStatus.SENT
                    || target == NotificationStatus.FAILED
                    || target == NotificationStatus.CANCELLED;
            case SENT -> target == NotificationStatus.DELIVERED
                    || target == NotificationStatus.FAILED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    @Override
    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        User user = userRepository.findById(request.userId()).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(request.userId())));

        Reservation reservation = null;
        if (request.reservationId() != null) {
            reservation = reservationRepository.findById(request.reservationId()).orElseThrow(
                    () -> new ResourceNotFoundException("Reservation with id '%s' not found".formatted(request.reservationId())));

            if (!reservation.getUser().getId().equals(user.getId()))
                throw new BusinessRuleException("Reservation belongs to another user");
        }

        Loan loan = null;
        if (request.loanId() != null) {
            loan = loanRepository.findById(request.loanId()).orElseThrow(
                    () -> new ResourceNotFoundException("Loan with id '%s' not found".formatted(request.loanId())));

            if (!loan.getUser().getId().equals(user.getId()))
                throw new BusinessRuleException("Loan belongs to another user");
        }

        Fine fine = null;
        if (request.fineId() != null) {
            fine = fineRepository.findById(request.fineId()).orElseThrow(
                    () -> new ResourceNotFoundException("Fine with id '%s' not found".formatted(request.fineId())));

            if (!fine.getUser().getId().equals(user.getId()))
                throw new BusinessRuleException("Fine belongs to another user");
        }

        String subject = normalize(request.subject(), "Subject");
        String body = normalize(request.body(), "Notification body");

        if (request.channel() == NotificationChannel.EMAIL && subject == null)
            throw new BusinessRuleException("Email notification subject must not be blank");

        CreateNotificationRequest normalizedRequest = new CreateNotificationRequest(user.getId(), request.reservationId(),
                request.loanId(), request.fineId(), request.type(), request.channel(), subject, body);

        Notification notification = mapper.toEntity(normalizedRequest, user, reservation, loan, fine);
        notification.setStatus(NotificationStatus.PENDING);

        Notification saved = repository.save(notification);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long id) {
        Notification notification = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));

        return mapper.toResponse(notification);
    }

    @Override
    @Transactional
    public NotificationResponse updateStatus(Long id, UpdateNotificationStatusRequest request) {
        Notification notification = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));

        NotificationStatus targetStatus = request.status();

        if (!isTransitionAllowed(notification.getStatus(), targetStatus))
            throw new BusinessRuleException(
                    "Notification status transition from '%s' to '%s' is not allowed".formatted(notification.getStatus(), targetStatus));

        LocalDateTime sentAt = null;

        if ((targetStatus == NotificationStatus.SENT || targetStatus == NotificationStatus.DELIVERED)
                && notification.getSentAt() == null)
            sentAt = LocalDateTime.now();

        mapper.updateStatus(request, sentAt, notification);

        Notification saved = repository.save(notification);
        return mapper.toResponse(saved);
    }
}
