package me.ifmo.backend.user.persistence;

import jakarta.persistence.LockModeType;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE users SET token_version = token_version + 1 WHERE id = :userId
        """, nativeQuery = true)
    void incrementTokenVersion(@Param("userId") Long userId);
}
