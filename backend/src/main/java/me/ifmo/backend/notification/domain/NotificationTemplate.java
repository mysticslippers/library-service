package me.ifmo.backend.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationTemplateStatus;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, columnDefinition = "notification_type")
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "channel", nullable = false, columnDefinition = "notification_channel")
    private NotificationChannel channel;

    @Column(name = "subject_template", length = 255)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_parameters", nullable = false, columnDefinition = "jsonb")
    private List<String> requiredParameters = List.of();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "notification_template_status")
    private NotificationTemplateStatus status = NotificationTemplateStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return "NotificationTemplate{" +
                "id=" + id +
                ", type=" + type +
                ", channel=" + channel +
                ", subjectTemplate='" + subjectTemplate + '\'' +
                ", bodyTemplate='" + bodyTemplate + '\'' +
                ", requiredParameters=" + requiredParameters +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        NotificationTemplate that = (NotificationTemplate) object;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
