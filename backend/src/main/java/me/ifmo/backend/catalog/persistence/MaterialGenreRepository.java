package me.ifmo.backend.catalog.persistence;

import me.ifmo.backend.catalog.domain.MaterialGenre;
import me.ifmo.backend.catalog.domain.id.MaterialGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialGenreRepository extends JpaRepository<MaterialGenre, MaterialGenreId> {

    List<MaterialGenre> findByMaterial_Id(Long materialId);

    void deleteByMaterial_Id(Long materialId);

}