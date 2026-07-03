package me.ifmo.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_blocks")
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "blocked_at", nullable = false, updatable = false)
    private LocalDateTime blockedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unblocked_by_user_id")
    private User unblockedByUser;

    @Column(name = "unblock_reason", columnDefinition = "text")
    private String unblockReason;

    @Column(name = "unblocked_at")
    private LocalDateTime unblockedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "user_block_status")
    private UserBlockStatus status = UserBlockStatus.ACTIVE;

    @Override
    public String toString() {
        return "UserBlock{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", createdByUserId=" + (createdByUser != null ? createdByUser.getId() : null) +
                ", reason='" + reason + '\'' +
                ", blockedAt=" + blockedAt +
                ", expiresAt=" + expiresAt +
                ", unblockedByUserId=" + (unblockedByUser != null ? unblockedByUser.getId() : null) +
                ", unblockReason='" + unblockReason + '\'' +
                ", unblockedAt=" + unblockedAt +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        UserBlock userBlock = (UserBlock) object;
        return id != null && Objects.equals(id, userBlock.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
