package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserWarningRequest;
import me.ifmo.backend.dto.user.request.CreateUserWarningRequest;
import me.ifmo.backend.dto.user.response.UserWarningResponse;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserWarning;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.entities.enums.UserWarningStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserWarningMapper;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.repositories.UserWarningRepository;
import me.ifmo.backend.services.UserWarningService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserWarningServiceImpl implements UserWarningService {

    private final UserWarningRepository repository;
    private final UserRepository userRepository;
    private final UserWarningMapper mapper;

    private String normalize(String value, String fieldName) {
        if(fieldName.equals("Comment")){
            if (value == null)
                return null;

            String normalized = value.strip();
            return normalized.isBlank() ? null : normalized;
        } else {
            if (value == null || value.strip().isBlank())
                throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

            return value.strip();
        }
    }

    private String normalizeNullable(String value) {
        if (value == null)
            return null;

        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    private User findTargetUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));

        if (user.getStatus() == UserStatus.ARCHIVED)
            throw new BusinessRuleException("Archived user cannot receive warnings");

        return user;
    }

    private User findActiveActor(Long id) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Created by user with id '%s' not found".formatted(id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("Created by user must be active");

        return user;
    }

    @Override
    @Transactional
    public UserWarningResponse create(Long createdByUserId, CreateUserWarningRequest request) {
        User user = findTargetUser(request.userId());
        User createdByUser = findActiveActor(createdByUserId);

        String reason = normalize(request.reason(), "Warning reason");
        String comment = normalize(request.comment(), "Comment");

        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Warning expiresAt must be in the future");

        CreateUserWarningRequest normalizedRequest = new CreateUserWarningRequest(user.getId(), reason, comment, request.expiresAt());

        UserWarning warning = mapper.toEntity(normalizedRequest, user, createdByUser);
        warning.setStatus(UserWarningStatus.ACTIVE);

        UserWarning saved = repository.save(warning);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserWarningResponse getUserWarningById(Long id) {
        UserWarning warning = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        return mapper.toResponse(warning);
    }

    @Override
    @Transactional
    public UserWarningResponse cancel(Long id, CancelUserWarningRequest request) {
        UserWarning warning = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        if (warning.getStatus() != UserWarningStatus.ACTIVE)
            throw new BusinessRuleException("Only active user warning can be cancelled");

        if (request.reason() != null)
            normalize(request.reason(), "Cancellation reason");

        warning.setStatus(UserWarningStatus.CANCELLED);

        UserWarning saved = repository.save(warning);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserWarningResponse expire(Long id) {
        UserWarning warning = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        if (warning.getStatus() != UserWarningStatus.ACTIVE)
            throw new BusinessRuleException("Only active user warning can be expired");

        if (warning.getExpiresAt() == null || warning.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("User warning has not expired yet");

        warning.setStatus(UserWarningStatus.EXPIRED);

        UserWarning saved = repository.save(warning);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserWarningResponse> search(Long userId, Long createdByUserId, UserWarningStatus status, Pageable pageable) {
        Page<UserWarningResponse> responses = repository.search(userId, createdByUserId, status, pageable).map(mapper::toResponse);
        return PageResponse.from(responses);
    }
}
