package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.MaterialGenre;
import me.ifmo.backend.entities.id.MaterialGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialGenreRepository extends JpaRepository<MaterialGenre, MaterialGenreId> {

    List<MaterialGenre> findByMaterial_Id(Long materialId);

    void deleteByMaterial_Id(Long materialId);

}