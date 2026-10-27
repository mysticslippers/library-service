package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;

import me.ifmo.backend.fine.domain.Fine;
import me.ifmo.backend.fine.persistence.FineRepository;

import me.ifmo.backend.circulation.domain.Loan;
import me.ifmo.backend.circulation.domain.Reservation;
import me.ifmo.backend.circulation.persistence.LoanRepository;
import me.ifmo.backend.circulation.persistence.ReservationRepository;

import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.notification.domain.Notification;
import me.ifmo.backend.notification.domain.NotificationTemplate;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationStatus;
import me.ifmo.backend.notification.domain.enums.NotificationTemplateStatus;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.persistence.NotificationRepository;
import me.ifmo.backend.notification.persistence.NotificationTemplateRepository;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.notification.web.request.NotificationSearchRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationStatusRequest;
import me.ifmo.backend.notification.web.response.NotificationResponse;
import me.ifmo.backend.shared.exceptions.BusinessRuleException;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import me.ifmo.backend.notification.mapper.NotificationMapper;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.notification.application.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private record NotificationContent(String subject, String body) {}

    private static final Pattern TEMPLATE_PARAMETER_PATTERN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceServiceImpl preferenceService;
    private final NotificationMapper notificationMapper;
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
        StringBuilder rendered = new StringBuilder();
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

    @Override
    @Transactional
    public NotificationResponse createAuth(CreateNotificationRequest request, Long authTokenId, LocalDateTime secretExpiresAt) {
        if (request.type() != NotificationType.ACCOUNT_ACTIVATION && request.type() != NotificationType.PASSWORD_RECOVERY)
            throw new BusinessRuleException("Authentication notification type is required");

        if (authTokenId == null || secretExpiresAt == null)
            throw new BusinessRuleException("Authentication notification requires its token and expiration");

        NotificationResponse response = create(request);
        Notification notification = repository.findById(response.id()).orElseThrow();
        notification.setAuthTokenId(authTokenId);
        notification.setSecretExpiresAt(secretExpiresAt);
        return response;
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

        Notification notification = notificationMapper.toEntity(normalizedRequest, user, reservation, loan, fine);
        if (preferenceService.isNotificationEnabled(user.getId(), request.type(), channel)) {
            notification.setStatus(NotificationStatus.PENDING);
        } else {
            notification.setStatus(NotificationStatus.CANCELLED);
            notification.setErrorMessage("Disabled by user notification preferences");
        }

        Notification saved = repository.save(notification);
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long actorUserId, Long id) {
        User actor = findActor(actorUserId);
        Notification notification = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));
        validateCanView(actor, notification);

        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public NotificationResponse updateStatus(Long id, UpdateNotificationStatusRequest request) {
        Notification notification = repository.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));

        if (request.status() != NotificationStatus.CANCELLED)
            throw new BusinessRuleException("Delivery statuses are managed by the delivery worker; only cancellation is allowed");
        if (!Set.of(NotificationStatus.PLANNED, NotificationStatus.PENDING, NotificationStatus.FAILED,
                NotificationStatus.CANCELLED).contains(notification.getStatus()))
            throw new BusinessRuleException("Only an unsent notification can be cancelled");

        notification.setStatus(NotificationStatus.CANCELLED);

        Notification saved = repository.save(notification);
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long actorUserId, Long id) {
        User actor = findActor(actorUserId);
        Notification notification = repository.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));
        validateCanView(actor, notification);

        if (notification.getReadAt() == null)
            notification.setReadAt(LocalDateTime.now());

        Notification saved = repository.save(notification);
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NotificationResponse resend(Long actorUserId, Long id) {
        User actor = findActor(actorUserId);
        validateStaff(actor);

        Notification notification = repository.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification with id '%s' not found".formatted(id)));

        if (notification.getType() == NotificationType.ACCOUNT_ACTIVATION || notification.getType() == NotificationType.PASSWORD_RECOVERY)
            throw new BusinessRuleException("Request a new authentication message through the authentication endpoints");

        if (notification.getStatus() != NotificationStatus.FAILED && notification.getStatus() != NotificationStatus.UNDELIVERED)
            throw new BusinessRuleException("Only failed or undelivered notification can be resent");

        if (notification.getAttemptCount() >= maxDeliveryAttempts)
            throw new BusinessRuleException("Notification delivery attempt limit has been reached");

        notification.setStatus(NotificationStatus.PENDING);
        notification.setErrorMessage(null);
        notification.setExternalMessageId(null);
        notification.setSentAt(null);
        notification.setSendingStartedAt(null);

        Notification saved = repository.save(notification);
        auditLogService.record(actor.getId(), AuditEntityType.NOTIFICATION, saved.getId(), AuditAction.UPDATE,
                Map.of("action", "RESEND"));
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(Long actorUserId, NotificationSearchRequest request, Pageable pageable) {
        User actor = findActor(actorUserId);
        String normalizedQuery = request.query() != null ? request.query().strip().toLowerCase(Locale.ROOT) : "";
        Long userId = request.userId();

        if (isStaff(actor)) {
            if (userId != null && !userId.equals(actor.getId()))
                throw new AccessDeniedException("Access is denied");

            userId = actor.getId();
        }

        Long filterUserId = userId;
        Specification<Notification> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.not(
                        root.get("type").in(NotificationType.ACCOUNT_ACTIVATION, NotificationType.PASSWORD_RECOVERY)
                );

        if (filterUserId != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("user").get("id"), filterUserId));
        if (request.reservationId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("reservation").get("id"), request.reservationId()));
        if (request.loanId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("loan").get("id"), request.loanId()));
        if (request.fineId() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("fine").get("id"), request.fineId()));
        if (request.type() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("type"), request.type()));
        if (request.channel() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("channel"), request.channel()));
        if (request.status() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), request.status()));
        if (request.createdFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
        if (request.createdTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));
        if (request.sentFrom() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("sentAt"), request.sentFrom()));
        if (request.sentTo() != null)
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("sentAt"), request.sentTo()));
        if (!normalizedQuery.isBlank()) {
            String pattern = "%" + normalizedQuery + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(
                            criteriaBuilder.coalesce(root.get("subject"), "")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(
                            criteriaBuilder.coalesce(root.get("body"), "")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("user").get("email")), pattern)
            ));
        }

        Page<Notification> notifications = repository.findAll(specification, pageable);

        Page<NotificationResponse> responses = notifications.map(notificationMapper::toResponse);

        return PageResponse.from(responses);
    }
}
