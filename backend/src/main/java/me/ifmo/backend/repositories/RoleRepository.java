package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.enums.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);

    boolean existsByCode(RoleCode code);
}
