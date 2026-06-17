package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Fine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FineRepository extends JpaRepository<Fine, Long> {

    Page<Fine> findByUser_Id(Long userId, Pageable pageable);
}
