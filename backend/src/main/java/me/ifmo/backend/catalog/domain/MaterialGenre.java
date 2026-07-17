package me.ifmo.backend.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import me.ifmo.backend.catalog.domain.id.MaterialGenreId;

import java.util.Objects;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "material_genres")
public class MaterialGenre {

    @EmbeddedId
    private MaterialGenreId id;

    @MapsId("materialId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @MapsId("genreId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @Override
    public String toString() {
        return "MaterialGenre{" +
                "id=" + id +
                ", materialId=" + (material != null ? material.getId() : null) +
                ", genreId=" + (genre != null ? genre.getId() : null) +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        MaterialGenre that = (MaterialGenre) object;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}