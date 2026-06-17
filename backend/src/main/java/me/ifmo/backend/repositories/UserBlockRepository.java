package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
}
