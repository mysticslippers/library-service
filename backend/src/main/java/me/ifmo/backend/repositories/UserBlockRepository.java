package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserBlock;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Page<UserBlock> findByUser_Id(Long userId, Pageable pageable);

    Optional<UserBlock> findByUser_IdAndStatus(Long userId, UserBlockStatus status);

    boolean existsByUser_IdAndStatus(Long userId, UserBlockStatus status);

    Page<UserBlock> findByStatus(UserBlockStatus status, Pageable pageable);

    Page<UserBlock> findByCreatedByUser_Id(Long createdByUserId, Pageable pageable);

    Page<UserBlock> findByBlockedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
