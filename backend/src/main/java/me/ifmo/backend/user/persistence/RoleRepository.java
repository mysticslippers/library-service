package me.ifmo.backend.user.persistence;

import me.ifmo.backend.user.domain.Role;
import me.ifmo.backend.user.domain.enums.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);

}