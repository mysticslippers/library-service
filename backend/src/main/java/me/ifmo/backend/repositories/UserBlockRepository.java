package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserBlock;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Page<UserBlock> findByUser_Id(Long userId, Pageable pageable);

    Optional<UserBlock> findByUser_IdAndStatus(Long userId, UserBlockStatus status);

    boolean existsByUser_IdAndStatus(Long userId, UserBlockStatus status);

    Page<UserBlock> findByStatus(UserBlockStatus status, Pageable pageable);

    @Query("""
       SELECT userBlock FROM UserBlock userBlock
           WHERE (:userId IS NULL OR userBlock.user.id = :userId)
             AND (:createdByUserId IS NULL OR userBlock.createdByUser.id = :createdByUserId)
             AND (:status IS NULL OR userBlock.status = :status)
    """)
    Page<UserBlock> search(@Param("userId") Long userId,
                           @Param("createdByUserId") Long createdByUserId,
                           @Param("status") UserBlockStatus status,
                           Pageable pageable);
}
