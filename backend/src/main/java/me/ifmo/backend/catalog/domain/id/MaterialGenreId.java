package me.ifmo.backend.catalog.domain.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@ToString
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MaterialGenreId implements Serializable {

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "genre_id")
    private Long genreId;
}