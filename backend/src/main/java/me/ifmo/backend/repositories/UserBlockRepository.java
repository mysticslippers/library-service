package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Page<UserBlock> findByUser_Id(Long userId, Pageable pageable);
}
