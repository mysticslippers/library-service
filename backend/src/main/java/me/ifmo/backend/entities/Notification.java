package me.ifmo.backend.entities;

import me.ifmo.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.entities.enums.NotificationChannel;
import me.ifmo.backend.entities.enums.NotificationStatus;
import me.ifmo.backend.entities.enums.NotificationType;
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
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fine_id")
    private Fine fine;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, columnDefinition = "notification_type")
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "channel", nullable = false, columnDefinition = "notification_channel")
    private NotificationChannel channel;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "notification_status")
    private NotificationStatus status = NotificationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "external_message_id")
    private String externalMessageId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Override
    public String toString() {
        return "Notification{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", reservationId=" + (reservation != null ? reservation.getId() : null) +
                ", loanId=" + (loan != null ? loan.getId() : null) +
                ", fineId=" + (fine != null ? fine.getId() : null) +
                ", type=" + type +
                ", channel=" + channel +
                ", subject='" + subject + '\'' +
                ", body='" + body + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", sentAt=" + sentAt +
                ", readAt=" + readAt +
                ", externalMessageId='" + externalMessageId + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", attemptCount=" + attemptCount +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Notification that = (Notification) object;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
