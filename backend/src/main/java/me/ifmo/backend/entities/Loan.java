package me.ifmo.backend.entities;

import me.ifmo.backend.library.internal.domain.Branch;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.entities.enums.LoanStatus;
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
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id", nullable = false)
    private MaterialCopy copy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by_user_id", nullable = false)
    private User issuedByUser;

    @CreationTimestamp
    @Column(name = "loaned_at", nullable = false, updatable = false)
    private LocalDateTime loanedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Builder.Default
    @Column(name = "renewal_count", nullable = false)
    private Integer renewalCount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "loan_status")
    private LoanStatus status = LoanStatus.ACTIVE;

    @Override
    public String toString() {
        return "Loan{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", copyId=" + (copy != null ? copy.getId() : null) +
                ", reservationId=" + (reservation != null ? reservation.getId() : null) +
                ", branchId=" + (branch != null ? branch.getId() : null) +
                ", issuedByUserId=" + (issuedByUser != null ? issuedByUser.getId() : null) +
                ", loanedAt=" + loanedAt +
                ", dueAt=" + dueAt +
                ", returnedAt=" + returnedAt +
                ", renewalCount=" + renewalCount +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Loan loan = (Loan) object;
        return id != null && Objects.equals(id, loan.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}