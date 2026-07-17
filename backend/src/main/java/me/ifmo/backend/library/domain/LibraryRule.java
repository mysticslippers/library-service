package me.ifmo.backend.library.domain;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.library.domain.enums.LibraryRuleStatus;
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
@Table(name = "library_rules")
public class LibraryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Builder.Default
    @Column(name = "max_active_reservations", nullable = false)
    private Integer maxActiveReservations = 5;

    @Builder.Default
    @Column(name = "max_active_loans", nullable = false)
    private Integer maxActiveLoans = 10;

    @Builder.Default
    @Column(name = "reservation_ttl_days", nullable = false)
    private Integer reservationTtlDays = 3;

    @Builder.Default
    @Column(name = "default_loan_days", nullable = false)
    private Integer defaultLoanDays = 14;

    @Builder.Default
    @Column(name = "renewal_allowed", nullable = false)
    private Boolean renewalAllowed = true;

    @Builder.Default
    @Column(name = "max_renewal_count", nullable = false)
    private Integer maxRenewalCount = 2;

    @Builder.Default
    @Column(name = "renewal_period_days", nullable = false)
    private Integer renewalPeriodDays = 7;

    @Builder.Default
    @Column(name = "reservation_allowed", nullable = false)
    private Boolean reservationAllowed = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "library_rule_status")
    private LibraryRuleStatus status = LibraryRuleStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Override
    public String toString() {
        return "LibraryRule{" +
                "id=" + id +
                ", branchId=" + (branch != null ? branch.getId() : null) +
                ", maxActiveReservations=" + maxActiveReservations +
                ", maxActiveLoans=" + maxActiveLoans +
                ", reservationTtlDays=" + reservationTtlDays +
                ", defaultLoanDays=" + defaultLoanDays +
                ", renewalAllowed=" + renewalAllowed +
                ", maxRenewalCount=" + maxRenewalCount +
                ", renewalPeriodDays=" + renewalPeriodDays +
                ", reservationAllowed=" + reservationAllowed +
                ", status=" + status +
                ", validFrom=" + validFrom +
                ", validTo=" + validTo +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        LibraryRule libraryRule = (LibraryRule) object;
        return id != null && Objects.equals(id, libraryRule.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
