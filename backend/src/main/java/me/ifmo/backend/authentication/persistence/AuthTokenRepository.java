package me.ifmo.backend.authentication.persistence;

import me.ifmo.backend.authentication.domain.AuthToken;
import me.ifmo.backend.authentication.domain.enums.AuthTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @Query("SELECT token.user.id FROM AuthToken token WHERE token.token = :token AND token.type = :type")
    Optional<Long> findUserIdByTokenAndType(@Param("token") String token, @Param("type") AuthTokenType type);

    Optional<AuthToken> findByTokenAndType(String token, AuthTokenType type);

    List<AuthToken> findByUser_IdAndTypeAndUsedAtIsNull(Long userId, AuthTokenType type);
}
