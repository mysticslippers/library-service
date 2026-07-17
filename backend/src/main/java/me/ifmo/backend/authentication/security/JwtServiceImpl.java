package me.ifmo.backend.authentication.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.domain.enums.RoleCode;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
public class JwtServiceImpl  implements JwtService {

    private final SecretKey key;
    private final long accessTokenExpiresIn;

    public JwtServiceImpl(@Value("${security.jwt.secret}") String secret, @Value("${security.jwt.access-token-expiration-ms}") long accessTokenExpiresIn) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Override
    public String generate(User user, Collection<RoleCode> roles) {
        Instant now = Instant.now();

        List<String> roleNames = roles.stream().map(RoleCode::name).toList();

        return Jwts.builder().subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiresIn)))
                .signWith(key)
                .compact();
    }

    @Override
    public String extract(String token) {
        return parse(token).getSubject();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }
}
