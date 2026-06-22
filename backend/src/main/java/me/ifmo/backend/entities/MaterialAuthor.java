package me.ifmo.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.entities.id.MaterialAuthorId;

import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "material_authors")
public class MaterialAuthor {

    @EmbeddedId
    private MaterialAuthorId id;

    @MapsId("materialId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @MapsId("authorId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    @Builder.Default
    @Column(name = "author_order", nullable = false)
    private Integer authorOrder = 1;

    @Override
    public String toString() {
        return "MaterialAuthor{" +
                "id=" + id +
                ", materialId=" + (material != null ? material.getId() : null) +
                ", authorId=" + (author != null ? author.getId() : null) +
                ", authorOrder=" + authorOrder +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        MaterialAuthor that = (MaterialAuthor) object;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}