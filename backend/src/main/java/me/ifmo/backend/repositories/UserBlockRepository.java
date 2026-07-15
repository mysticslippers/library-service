package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserBlock;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long>, JpaSpecificationExecutor<UserBlock> {

    Page<UserBlock> findByUser_Id(Long userId, Pageable pageable);

    Optional<UserBlock> findByUser_IdAndStatus(Long userId, UserBlockStatus status);

    boolean existsByUser_IdAndStatus(Long userId, UserBlockStatus status);

    Page<UserBlock> findByStatus(UserBlockStatus status, Pageable pageable);

}
