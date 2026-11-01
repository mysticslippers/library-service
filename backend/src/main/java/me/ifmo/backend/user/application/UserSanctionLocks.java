package me.ifmo.backend.user.application;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.exceptions.ResourceNotFoundException;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserBlock;
import me.ifmo.backend.user.domain.UserWarning;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserWarningRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class UserSanctionLocks {

    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final UserWarningRepository warnings;
    private final EntityManager entityManager;

    public User user(Long id) {
        User user = users.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("User with id '%s' not found".formatted(id)));

        entityManager.refresh(user);
        return user;
    }

    public UserBlock block(Long id) {
        Long userId = blocks.findUserIdById(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        user(userId);
        UserBlock block = blocks.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("User block with id '%s' not found".formatted(id)));

        entityManager.refresh(block);
        return block;
    }

    public UserWarning warning(Long id) {
        Long userId = warnings.findUserIdById(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        user(userId);
        UserWarning warning = warnings.findByIdForUpdate(id).orElseThrow(
                () -> new ResourceNotFoundException("User warning with id '%s' not found".formatted(id)));

        entityManager.refresh(warning);
        return warning;
    }
}
