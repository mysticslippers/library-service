package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserBlockRequest;
import me.ifmo.backend.dto.user.request.CreateUserBlockRequest;
import me.ifmo.backend.dto.user.response.UserBlockResponse;
import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.UserBlock;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import me.ifmo.backend.entities.enums.UserStatus;
import me.ifmo.backend.exceptions.domain.BusinessRuleException;
import me.ifmo.backend.exceptions.domain.DuplicateResourceException;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.UserBlockMapper;
import me.ifmo.backend.repositories.UserBlockRepository;
import me.ifmo.backend.repositories.UserRepository;
import me.ifmo.backend.services.UserBlockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserBlockServiceImpl implements UserBlockService {

    private final UserBlockRepository repository;
    private final UserRepository userRepository;
    private final UserBlockMapper mapper;

    private String normalize(String value, String fieldName) {
        if (value == null || value.strip().isBlank())
            throw new BusinessRuleException("%s must not be blank".formatted(fieldName));

        return value.strip();
    }

    private User findActiveUser(Long id, String fieldName) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("%s with id '%s' not found".formatted(fieldName, id)));

        if (user.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("%s must be active".formatted(fieldName));

        return user;
    }

    private void activateUserIfBlocked(User user) {
        if (user.getStatus() == UserStatus.BLOCKED)
            user.setStatus(UserStatus.ACTIVE);
    }

    @Override
    @Transactional
    public UserBlockResponse create(Long createdByUserId, CreateUserBlockRequest request) {
        User user = findActiveUser(request.userId(), "User");
        User createdByUser = findActiveUser(createdByUserId, "Created by user");

        if (repository.existsByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE))
            throw new DuplicateResourceException("User with id '%s' already has active block".formatted(user.getId()));

        String reason = normalize(request.reason(), "Block reason");

        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("Block expiresAt must be in the future");

        CreateUserBlockRequest normalizedRequest = new CreateUserBlockRequest(user.getId(), reason, request.expiresAt());

        UserBlock block = mapper.toEntity(normalizedRequest, user, createdByUser);
        block.setStatus(UserBlockStatus.ACTIVE);
        user.setStatus(UserStatus.BLOCKED);

        UserBlock saved = repository.save(block);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserBlockResponse getUserBlockById(Long id) {
        UserBlock block = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        return mapper.toResponse(block);
    }

    @Override
    @Transactional
    public UserBlockResponse cancel(Long id, CancelUserBlockRequest request) {
        UserBlock block = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        if (block.getStatus() != UserBlockStatus.ACTIVE)
            throw new BusinessRuleException("Only active user block can be cancelled");

        if (request.reason() != null)
            normalize(request.reason(), "Cancellation reason");

        block.setStatus(UserBlockStatus.CANCELLED);
        block.setUnblockedAt(LocalDateTime.now());
        activateUserIfBlocked(block.getUser());

        UserBlock saved = repository.save(block);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserBlockResponse expire(Long id) {
        UserBlock block = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        if (block.getStatus() != UserBlockStatus.ACTIVE)
            throw new BusinessRuleException("Only active user block can be expired");

        if (block.getExpiresAt() == null || block.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessRuleException("User block has not expired yet");

        block.setStatus(UserBlockStatus.EXPIRED);
        block.setUnblockedAt(LocalDateTime.now());
        activateUserIfBlocked(block.getUser());

        UserBlock saved = repository.save(block);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserBlockResponse> search(Long userId, Long createdByUserId, UserBlockStatus status, Pageable pageable) {
        Page<UserBlockResponse> responses = repository.search(userId, createdByUserId, status, pageable).map(mapper::toResponse);
        return PageResponse.from(responses);
    }
}
