package me.ifmo.backend.user.persistence;

import me.ifmo.backend.user.domain.UserWarning;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
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

public interface UserWarningRepository extends JpaRepository<UserWarning, Long>, JpaSpecificationExecutor<UserWarning> {

    @Query("SELECT warning.id FROM UserWarning warning WHERE warning.status = :status AND warning.expiresAt <= :now ORDER BY warning.expiresAt, warning.id")
    List<Long> findDueIds(@Param("status") UserWarningStatus status, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT warning.user.id FROM UserWarning warning WHERE warning.id = :id")
    Optional<Long> findUserIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT warning FROM UserWarning warning WHERE warning.id = :id")
    Optional<UserWarning> findByIdForUpdate(@Param("id") Long id);

    Page<UserWarning> findByUser_Id(Long userId, Pageable pageable);

    Page<UserWarning> findByUser_IdAndStatus(Long userId, UserWarningStatus status, Pageable pageable);

    Page<UserWarning> findByStatus(UserWarningStatus status, Pageable pageable);

}
