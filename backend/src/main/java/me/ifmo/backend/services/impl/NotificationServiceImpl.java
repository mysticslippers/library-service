package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.notification.request.CreateNotificationRequest;
import me.ifmo.backend.dto.notification.request.NotificationSearchRequest;
import me.ifmo.backend.dto.notification.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.dto.notification.response.NotificationResponse;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.entities.enums.*;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.NotificationMapper;
import me.ifmo.backend.repositories.*;
import me.ifmo.backend.services.AuditLogService;
import me.ifmo.backend.services.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Set<NotificationStatus> FINAL_STATUSES =
            Set.of(NotificationStatus.DELIVERED, NotificationStatus.UNDELIVERED, NotificationStatus.CANCELLED);
    private static final Pattern TEMPLATE_PARAMETER_PATTERN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceServiceImpl preferenceService;
    private final NotificationMapper mapper;
    private final AuditLogService auditLogService;

    @Value("${notification.delivery.max-attempts:3}")
    private int maxDeliveryAttempts;

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private String normalize(String value) {
        if (value == null)
            return null;

        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private String parameterValue(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value == null)
            throw new BusinessRuleException("Notification template parameter '%s' is required".formatted(key));

        String stringValue = value.toString();
        if (stringValue.isBlank())
            throw new BusinessRuleException("Notification template parameter '%s' must not be blank".formatted(key));

        return stringValue;
    }

    private String renderTemplate(String template, Map<String, Object> parameters) {
        if (template == null)
            return null;

        Matcher matcher = TEMPLATE_PARAMETER_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String replacement = parameterValue(parameters, matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private NotificationContent resolveContent(CreateNotificationRequest request, NotificationChannel channel) {
        Map<String, Object> parameters = request.parameters() != null ? request.parameters() : Map.of();
        String subject = normalize(request.subject());
        String body = normalize(request.body());

        if (subject != null && body != null)
            return new NotificationContent(subject, body);

        NotificationTemplate template = templateRepository.findByTypeAndChannelAndStatus(
                        request.type(), channel, NotificationTemplateStatus.ACTIVE).orElse(null);

        if (template != null) {
            for (String requiredParameter : template.getRequiredParameters())
                parameterValue(parameters, requiredParameter);

            if (subject == null)
                subject = renderTemplate(template.getSubjectTemplate(), parameters);
            if (body == null)
                body = renderTemplate(template.getBodyTemplate(), parameters);
        }

        body = normalizeRequired(body, "Notification body");

        if (channel == NotificationChannel.EMAIL)
            subject = normalizeRequired(subject, "Email notification subject");

        return new NotificationContent(subject, body);
    }

    private int roleRank(RoleCode roleCode) {
        return switch (roleCode) {
            case READER -> 1;
            case LIBRARIAN -> 2;
            case ADMIN -> 3;
        };
    }

    private int maxRoleRank(Long userId) {
        return userRoleRepository.findRoleCodesByUser_Id(userId).stream()
                .mapToInt(this::roleRank)
                .max()
                .orElse(0);
    }

    private boolean isStaff(User user) {
        return maxRoleRank(user.getId()) < roleRank(RoleCode.LIBRARIAN);
    }

    private User findActor(Long actorUserId) {
        User actor = userRepository.findById(actorUserId).orElseThrow(
                () -> new ResourceNotFoundException("Actor user with id '%s' not found".formatted(actorUserId)));

        if (actor.getStatus() != UserStatus.ACTIVE)
            throw new AccessDeniedException("Actor user must be active");

        return actor;
    }

    private void validateCanView(User actor, Notification notification) {
        if (!notification.getUser().getId().equals(actor.getId()) && isStaff(actor))
            throw new AccessDeniedException("Access is denied");
    }

    private void validateStaff(User actor) {
        if (isStaff(actor))
            throw new AccessDeniedException("Access is denied");
    }

    private boolean isTransitionAllowed(NotificationStatus current, NotificationStatus target) {
        if (current == target)
            return true;

        if (FINAL_STATUSES.contains(current))
            return false;

        return switch (current) {
            case PLANNED, FAILED -> target == NotificationStatus.PENDING
                    || target == NotificationStatus.UNDELIVERED
                    || target == NotificationStatus.CANCELLED;
            case PENDING -> target == NotificationStatus.SENT
                    || target == NotificationStatus.FAILED
                    || target == NotificationStatus.UNDELIVERED
                    || target == NotificationStatus.CANCELLED;
            case SENT -> target == NotificationStatus.DELIVERED
                    || target == NotificationStatus.FAILED;
            case DELIVERED, UNDELIVERED, CANCELLED -> false;
        };
    }

    private boolean isDeliveryAttemptStatus(NotificationStatus status) {
        return status == NotificationStatus.SENT
                || status == NotificationStatus.FAILED
                || status == NotificationStatus.UNDELIVERED;
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

        NotificationChannel channel = preferenceService.resolveChannel(user.getId(), request.type(), request.channel());
        NotificationContent content = resolveContent(request, channel);

        CreateNotificationRequest normalizedRequest = new CreateNotificationRequest(user.getId(), request.reservationId(),
                request.loanId(), request.fineId(), request.type(), channel, content.subject(), content.body(),
                request.parameters() != null ? request.parameters() : Map.of());

        Notification notification = mapper.toEntity(normalizedRequest, user, reservation, loan, fine);
        if (preferenceService.isNotificationEnabled(user.getId(), request.type(), channel)) {
            notification.setStatus(NotificationStatus.PENDING);
        } else {
            notification.setStatus(NotificationStatus.CANCELLED);
            notification.setErrorMessage("Disabled by user notification preferences");
        }

        Notification saved = repository.save(notification);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long actorUserId, Long id) {
        User actor = findActor(actorUserId);
        Notification notification = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));
        validateCanView(actor, notification);

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

        String externalMessageId = request.externalMessageId() != null ? request.externalMessageId().strip() : null;
        String errorMessage = request.errorMessage() != null ? request.errorMessage().strip() : null;

        if ((targetStatus == NotificationStatus.FAILED || targetStatus == NotificationStatus.UNDELIVERED)
                && (errorMessage == null || errorMessage.isBlank()))
            throw new BusinessRuleException("Delivery error message must not be blank");

        if ((targetStatus == NotificationStatus.SENT || targetStatus == NotificationStatus.DELIVERED)
                && (externalMessageId == null || externalMessageId.isBlank())
                && notification.getExternalMessageId() == null)
            throw new BusinessRuleException("External message id must not be blank");

        LocalDateTime sentAt = null;

        if ((targetStatus == NotificationStatus.SENT || targetStatus == NotificationStatus.DELIVERED)
                && notification.getSentAt() == null)
            sentAt = LocalDateTime.now();

        if (notification.getAttemptCount() == null)
            notification.setAttemptCount(0);

        if (isDeliveryAttemptStatus(targetStatus) && notification.getStatus() != targetStatus)
            notification.setAttemptCount(notification.getAttemptCount() + 1);

        if (targetStatus == NotificationStatus.FAILED && notification.getAttemptCount() >= maxDeliveryAttempts)
            request = new UpdateNotificationStatusRequest(NotificationStatus.UNDELIVERED,
                    externalMessageId, errorMessage);
        else
            request = new UpdateNotificationStatusRequest(targetStatus, externalMessageId, errorMessage);

        mapper.updateStatus(request, sentAt, notification);

        if (notification.getStatus() == NotificationStatus.SENT || notification.getStatus() == NotificationStatus.DELIVERED)
            notification.setErrorMessage(null);

        Notification saved = repository.save(notification);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long actorUserId, Long id) {
        User actor = findActor(actorUserId);
        Notification notification = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));
        validateCanView(actor, notification);

        if (notification.getReadAt() == null)
            notification.setReadAt(LocalDateTime.now());

        Notification saved = repository.save(notification);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NotificationResponse resend(Long actorUserId, Long id) {
        User actor = findActor(actorUserId);
        validateStaff(actor);

        Notification notification = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));

        if (notification.getStatus() != NotificationStatus.FAILED && notification.getStatus() != NotificationStatus.UNDELIVERED)
            throw new BusinessRuleException("Only failed or undelivered notification can be resent");

        if (notification.getAttemptCount() >= maxDeliveryAttempts)
            throw new BusinessRuleException("Notification delivery attempt limit has been reached");

        notification.setStatus(NotificationStatus.PENDING);
        notification.setErrorMessage(null);
        notification.setExternalMessageId(null);

        Notification saved = repository.save(notification);
        auditLogService.record(actor.getId(), AuditEntityType.NOTIFICATION, saved.getId(), AuditAction.UPDATE,
                Map.of("action", "RESEND"));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(Long actorUserId, NotificationSearchRequest request, Pageable pageable) {
        User actor = findActor(actorUserId);
        String query = request.query() != null ? request.query().strip() : "";
        Long userId = request.userId();

        if (isStaff(actor)) {
            if (userId != null && !userId.equals(actor.getId()))
                throw new AccessDeniedException("Access is denied");

            userId = actor.getId();
        }

        Page<Notification> notifications = repository.search(userId, request.reservationId(),
                request.loanId(), request.fineId(), request.type(), request.channel(), request.status(),
                request.createdFrom(), request.createdTo(), request.sentFrom(), request.sentTo(), query, pageable);

        Page<NotificationResponse> responses = notifications.map(mapper::toResponse);

        return PageResponse.from(responses);
    }

    private record NotificationContent(String subject, String body) {
    }
}
