package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserRole;
import me.ifmo.backend.entities.enums.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUser_Id(Long userId);

    List<UserRole> findByRole_Code(RoleCode roleId);

    Optional<UserRole> findByUser_IdAndRole_Code(Long userId, RoleCode roleCode);

    boolean existsByUser_IdAndRole_Code(Long userId, RoleCode roleCode);
}
