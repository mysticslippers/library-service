package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserWarning;
import me.ifmo.backend.entities.enums.UserWarningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {

    Page<UserWarning> findByUser_Id(Long userId, Pageable pageable);

    Page<UserWarning> findByUser_IdAndStatus(Long userId, UserWarningStatus status, Pageable pageable);

    Long countByUser_IdAndStatus(Long userId, UserWarningStatus status);

    Page<UserWarning> findByStatus(UserWarningStatus status, Pageable pageable);

    Page<UserWarning> findByCreatedByUser_Id(Long createdByUserId, Pageable pageable);

    Page<UserWarning> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
