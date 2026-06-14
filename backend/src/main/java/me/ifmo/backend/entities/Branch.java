package me.ifmo.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.entities.enums.BranchStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.descriptor.jdbc.JsonJdbcType;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcType(JsonJdbcType.class)
    @Column(name = "address", nullable = false, columnDefinition = "jsonb")
    private String address;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "branch_status")
    private BranchStatus status = BranchStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "Branch{" +
                "id=" + id +
                ", libraryId=" + (library != null ? library.getId() : null) +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object object){
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Branch branch = (Branch) object;
        return id != null && Objects.equals(id, branch.id);
    }

    @Override
    public int hashCode(){
        return getClass().hashCode();
    }
}
