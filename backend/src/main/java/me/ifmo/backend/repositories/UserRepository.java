package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.User;
import me.ifmo.backend.entities.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    Page<User> findByBranchId(Long branchId, Pageable pageable);

    @Query("""
           SELECT user FROM User user
               WHERE (:query IS NULL OR :query = ''
                   OR lower(user.email) LIKE lower(concat('%', :query, '%')) 
                   OR user.phone LIKE concat('%', :query, '%') 
                   OR lower(user.firstName) LIKE lower(concat('%', :query, '%')) 
                   OR lower(user.lastName) LIKE lower(concat('%', :query, '%'))
                   OR lower(coalesce(user.middleName, '')) LIKE lower(concat('%', :query, '%')))
    """)
    Page<User> search(@Param("query") String query, Pageable pageable);
}
