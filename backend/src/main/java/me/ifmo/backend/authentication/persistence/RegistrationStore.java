package me.ifmo.backend.authentication.persistence;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.user.domain.User;
import me.ifmo.backend.user.persistence.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RegistrationStore {

    private final JdbcTemplate jdbc;
    private final UserRepository users;

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<User> insertIfAbsent(User user) {
        List<Long> userIds = jdbc.query("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, middle_name, status, registered_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING_ACTIVATION', timezone('UTC', clock_timestamp()))
                ON CONFLICT DO NOTHING RETURNING id
                """, (row, index) -> row.getLong("id"), user.getEmail(), user.getPhone(), user.getPasswordHash(),
                user.getFirstName(), user.getLastName(), user.getMiddleName());
        if (userIds.isEmpty()) {
            return Optional.empty();
        }

        return users.findById(userIds.get(0));
    }
}
