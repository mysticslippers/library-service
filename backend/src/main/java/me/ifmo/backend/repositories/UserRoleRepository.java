package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.entities.id.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUser_Id(Long userId);

    Optional<UserRole> findByUser_IdAndRole_Code(Long userId, RoleCode roleCode);

    boolean existsByUser_IdAndRole_Code(Long userId, RoleCode roleCode);

    @Query("""
        SELECT userRole.role.code FROM UserRole userRole
            WHERE userRole.user.id = :userId
    """)
    List<RoleCode> findRoleCodesByUser_Id(@Param("userId") Long userId);

}