package me.ifmo.backend.repositories;

import me.ifmo.backend.entities.AuthToken;
import me.ifmo.backend.entities.enums.AuthTokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenAndType(String token, AuthTokenType type);

    List<AuthToken> findByUser_IdAndTypeAndUsedAtIsNull(Long userId, AuthTokenType type);
}
