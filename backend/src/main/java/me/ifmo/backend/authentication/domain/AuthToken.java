package me.ifmo.backend.authentication.domain;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.authentication.domain.enums.AuthTokenType;
import me.ifmo.backend.user.domain.User;
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
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, columnDefinition = "auth_token_type")
    private AuthTokenType type;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "AuthToken{"
                + "id=" + id
                + ", user=" + user
                + ", token='" + token + '\''
                + ", type=" + type
                + ", expiresAt=" + expiresAt
                + ", usedAt=" + usedAt
                + ", createdAt=" + createdAt
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        AuthToken authToken = (AuthToken) object;
        return id != null && Objects.equals(id, authToken.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
