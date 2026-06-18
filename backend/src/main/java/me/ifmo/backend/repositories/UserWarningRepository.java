package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserWarning;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {

    Page<UserWarning> findByUser_Id(Long userId, Pageable pageable);
}
