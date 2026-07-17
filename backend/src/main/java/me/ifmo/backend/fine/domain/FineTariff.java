package me.ifmo.backend.fine.domain;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.fine.domain.enums.FineTariffStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;
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
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fine_tariffs")
public class FineTariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "violation_type", nullable = false, columnDefinition = "violation_type")
    private ViolationType violationType;

    @Column(name = "amount_per_day", precision = 10, scale = 2)
    private BigDecimal amountPerDay;

    @Column(name = "fixed_amount", precision = 10, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "max_amount", precision = 10, scale = 2)
    private BigDecimal maxAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "fine_tariff_status")
    private FineTariffStatus status = FineTariffStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        FineTariff that = (FineTariff) object;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}