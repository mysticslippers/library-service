package me.ifmo.backend.notification.application.impl;

import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.notification.web.request.CreateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.request.UpdateNotificationTemplateRequest;
import me.ifmo.backend.notification.web.response.NotificationTemplateResponse;
import me.ifmo.backend.notification.domain.NotificationTemplate;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationTemplateStatus;
import me.ifmo.backend.shared.error.BusinessRuleException;
import me.ifmo.backend.shared.error.DuplicateResourceException;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.notification.mapper.NotificationTemplateMapper;
import me.ifmo.backend.notification.persistence.NotificationTemplateRepository;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.notification.application.NotificationTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    private final NotificationTemplateRepository repository;
    private final NotificationTemplateMapper notificationTemplateMapper;
    private final AuditLogService auditLogService;

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private String normalizeOptional(String value) {
        if (value == null)
            return null;

        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private List<String> normalizeRequiredParameters(List<String> parameters) {
        if (parameters == null)
            return List.of();

        return parameters.stream()
                .map(parameter -> normalizeRequired(parameter, "Required parameter"))
                .distinct()
                .toList();
    }

    private Set<String> extractParameters(String text) {
        Set<String> parameters = new HashSet<>();
        if (text == null)
            return parameters;

        Matcher matcher = PARAMETER_PATTERN.matcher(text);
        while (matcher.find())
            parameters.add(matcher.group(1));

        return parameters;
    }

    private void validateTemplate(String subjectTemplate, String bodyTemplate, List<String> requiredParameters) {
        Set<String> placeholders = extractParameters(subjectTemplate);
        placeholders.addAll(extractParameters(bodyTemplate));

        for (String requiredParameter : requiredParameters) {
            if (!placeholders.contains(requiredParameter))
                throw new BusinessRuleException("Required parameter '%s' is not used in template".formatted(requiredParameter));
        }
    }

    @Override
    @Transactional
    public NotificationTemplateResponse create(Long actorUserId, CreateNotificationTemplateRequest request) {
        if (repository.existsByTypeAndChannelAndStatus(request.type(), request.channel(), NotificationTemplateStatus.ACTIVE))
            throw new DuplicateResourceException("Active notification template for type '%s' and channel '%s' already exists"
                    .formatted(request.type(), request.channel()));

        String subjectTemplate = normalizeOptional(request.subjectTemplate());
        String bodyTemplate = normalizeRequired(request.bodyTemplate(), "Template body");
        List<String> requiredParameters = normalizeRequiredParameters(request.requiredParameters());

        if (request.channel() == NotificationChannel.EMAIL && subjectTemplate == null)
            throw new BusinessRuleException("Email notification template subject must not be blank");

        validateTemplate(subjectTemplate, bodyTemplate, requiredParameters);

        NotificationTemplate template = notificationTemplateMapper.toEntity(new CreateNotificationTemplateRequest(request.type(),
                request.channel(), subjectTemplate, bodyTemplate, requiredParameters));
        template.setStatus(NotificationTemplateStatus.ACTIVE);

        NotificationTemplate saved = repository.save(template);
        auditLogService.record(actorUserId, AuditEntityType.NOTIFICATION_TEMPLATE, saved.getId(), AuditAction.CREATE,
                Map.of("type", saved.getType().name(), "channel", saved.getChannel().name()));
        return notificationTemplateMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NotificationTemplateResponse update(Long actorUserId, Long id, UpdateNotificationTemplateRequest request) {
        NotificationTemplate template = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification template with id '%s' not found".formatted(id)));

        if (template.getStatus() == NotificationTemplateStatus.ARCHIVED)
            throw new BusinessRuleException("Archived notification template cannot be updated");

        String subjectTemplate = request.subjectTemplate() != null
                ? normalizeOptional(request.subjectTemplate())
                : template.getSubjectTemplate();
        String bodyTemplate = request.bodyTemplate() != null
                ? normalizeRequired(request.bodyTemplate(), "Template body")
                : template.getBodyTemplate();
        List<String> requiredParameters = request.requiredParameters() != null
                ? normalizeRequiredParameters(request.requiredParameters())
                : template.getRequiredParameters();

        if (template.getChannel() == NotificationChannel.EMAIL && subjectTemplate == null)
            throw new BusinessRuleException("Email notification template subject must not be blank");

        validateTemplate(subjectTemplate, bodyTemplate, requiredParameters);

        notificationTemplateMapper.updateEntity(new UpdateNotificationTemplateRequest(subjectTemplate, bodyTemplate, requiredParameters), template);
        NotificationTemplate saved = repository.save(template);
        auditLogService.record(actorUserId, AuditEntityType.NOTIFICATION_TEMPLATE, saved.getId(), AuditAction.UPDATE,
                Map.of("type", saved.getType().name(), "channel", saved.getChannel().name()));
        return notificationTemplateMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NotificationTemplateResponse archive(Long actorUserId, Long id) {
        NotificationTemplate template = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification template with id '%s' not found".formatted(id)));

        template.setStatus(NotificationTemplateStatus.ARCHIVED);
        NotificationTemplate saved = repository.save(template);
        auditLogService.record(actorUserId, AuditEntityType.NOTIFICATION_TEMPLATE, saved.getId(), AuditAction.ARCHIVE,
                Map.of("type", saved.getType().name(), "channel", saved.getChannel().name()));
        return notificationTemplateMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplateById(Long id) {
        NotificationTemplate template = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Notification template with id '%s' not found".formatted(id)));

        return notificationTemplateMapper.toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationTemplateResponse> search(NotificationTemplateStatus status, Pageable pageable) {
        Page<NotificationTemplate> templates = status != null ? repository.findByStatus(status, pageable) : repository.findAll(pageable);
        return PageResponse.from(templates.map(notificationTemplateMapper::toResponse));
    }
}
