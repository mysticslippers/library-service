package me.ifmo.backend.user.application;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.audit.application.AuditLogService;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.UserBlock;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import me.ifmo.backend.user.domain.enums.UserStatus;
import me.ifmo.backend.user.persistence.UserBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserBlockExpiration {

    private final UserSanctionLocks locks;
    private final UserBlockRepository repository;
    private final EntityManager entityManager;
    private final AuditLogService audit;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional(timeout = 10)
    public boolean expireDue(Long id) {
        return expireLockedBlock(locks.block(id), null);
    }

    @Transactional(timeout = 10)
    public User refreshBlockedUser(Long id) {
        User user = locks.user(id);
        expireForLockedUser(user);
        return user;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expireForLockedUser(User user) {
        if (user.getStatus() != UserStatus.BLOCKED)
            return;

        repository.findByUser_IdAndStatus(user.getId(), UserBlockStatus.ACTIVE).ifPresent(block -> {
            entityManager.refresh(block);
            expireLockedBlock(block, null);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireLockedBlock(UserBlock block, Long actorUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (block.getStatus() != UserBlockStatus.ACTIVE || block.getExpiresAt() == null || block.getExpiresAt().isAfter(now))
            return false;

        block.setStatus(UserBlockStatus.EXPIRED);
        block.setUnblockedByUser(null);
        block.setUnblockReason("Block expired");
        block.setUnblockedAt(now);
        if (block.getUser().getStatus() == UserStatus.BLOCKED)
            block.getUser().setStatus(UserStatus.ACTIVE);

        repository.saveAndFlush(block);
        audit.record(actorUserId, AuditEntityType.USER_BLOCK, block.getId(), AuditAction.UNBLOCK,
                Map.of("userId", block.getUser().getId(), "reason", "Block expired"));

        notifications.create(new CreateNotificationRequest(block.getUser().getId(), null, null, null,
                NotificationType.USER_UNBLOCKED, NotificationChannel.EMAIL, "Library account unblocked",
                "Your account has been unblocked because the block expired."));

        return true;
    }
}
