package me.ifmo.backend.user.persistence;

import me.ifmo.backend.user.domain.UserBlock;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long>, JpaSpecificationExecutor<UserBlock> {

    @Query("SELECT block.id FROM UserBlock block WHERE block.status = :status AND block.expiresAt <= :now ORDER BY block.expiresAt, block.id")
    List<Long> findDueIds(@Param("status") UserBlockStatus status, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT block.user.id FROM UserBlock block WHERE block.id = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT block FROM UserBlock block WHERE block.id = :id")
    Optional<UserBlock> findByIdForUpdate(@Param("id") Long id);

    Page<UserBlock> findByUser_Id(Long userId, Pageable pageable);

    Optional<UserBlock> findByUser_IdAndStatus(Long userId, UserBlockStatus status);

    boolean existsByUser_IdAndStatus(Long userId, UserBlockStatus status);

    Page<UserBlock> findByStatus(UserBlockStatus status, Pageable pageable);

}
