package me.ifmo.backend.authentication.persistence;

import me.ifmo.backend.authentication.domain.AuthToken;
import me.ifmo.backend.authentication.domain.enums.AuthTokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenAndType(String token, AuthTokenType type);

    List<AuthToken> findByUser_IdAndTypeAndUsedAtIsNull(Long userId, AuthTokenType type);
}
