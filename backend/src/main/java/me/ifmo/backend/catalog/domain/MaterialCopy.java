package me.ifmo.backend.catalog.domain;

import me.ifmo.backend.library.domain.Branch;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.catalog.domain.enums.CopyStatus;
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
@Table(name = "material_copies")
public class MaterialCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "inventory_number", nullable = false, unique = true, length = 100)
    private String inventoryNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "copy_status")
    private CopyStatus status = CopyStatus.AVAILABLE;

    @Column(name = "shelf_location", length = 100)
    private String shelfLocation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return "MaterialCopy{" +
                "id=" + id +
                ", materialId=" + (material != null ? material.getId() : null) +
                ", branchId=" + (branch != null ? branch.getId() : null) +
                ", inventoryNumber='" + inventoryNumber + '\'' +
                ", status=" + status +
                ", shelfLocation='" + shelfLocation + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        MaterialCopy that = (MaterialCopy) object;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
