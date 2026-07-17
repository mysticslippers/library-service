package me.ifmo.backend.entities;

import me.ifmo.backend.catalog.domain.MaterialCopy;

import me.ifmo.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.entities.enums.FineStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fines")
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copy_id")
    private MaterialCopy copy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id")
    private FineTariff tariff;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "reason", nullable = false, columnDefinition = "violation_type")
    private ViolationType reason;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "fine_status")
    private FineStatus status = FineStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Override
    public String toString() {
        return "Fine{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", loanId=" + (loan != null ? loan.getId() : null) +
                ", copyId=" + (copy != null ? copy.getId() : null) +
                ", tariffId=" + (tariff != null ? tariff.getId() : null) +
                ", reason=" + reason +
                ", amount=" + amount +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", paidAt=" + paidAt +
                ", cancelledAt=" + cancelledAt +
                ", cancellationReason='" + cancellationReason + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object object){
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Fine fine = (Fine) object;
        return id != null && Objects.equals(id, fine.id);
    }

    @Override
    public int hashCode(){
        return getClass().hashCode();
    }
}
