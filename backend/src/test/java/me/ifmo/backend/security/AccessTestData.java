package me.ifmo.backend.security;

import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.circulation.application.LoanService;
import me.ifmo.backend.circulation.application.ReservationService;
import me.ifmo.backend.circulation.web.request.CreateLoanRequest;
import me.ifmo.backend.circulation.web.request.CreateReservationRequest;
import me.ifmo.backend.notification.application.NotificationService;
import me.ifmo.backend.notification.domain.enums.NotificationChannel;
import me.ifmo.backend.notification.domain.enums.NotificationType;
import me.ifmo.backend.notification.web.request.CreateNotificationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class AccessTestData {

    static final String PASSWORD = "Password123";
    private final JdbcTemplate jdbc;
    private final String passwordHash;
    private final ReservationService reservations;
    private final LoanService loans;
    private final NotificationService notifications;
    long reader;
    long otherReader;
    long librarian;
    long otherLibrarian;
    long admin;
    long branch;
    long material;
    private int sequence;

    AccessTestData(JdbcTemplate jdbc, PasswordEncoder encoder, ReservationService reservations,
                   LoanService loans, NotificationService notifications) {
        this.jdbc = jdbc;
        this.passwordHash = encoder.encode(PASSWORD);
        this.reservations = reservations;
        this.loans = loans;
        this.notifications = notifications;
    }

    void reset() {
        jdbc.execute("TRUNCATE users, libraries, roles, materials, authors, genres, fine_tariffs, "
                + "notification_templates, outbox_events, processed_events, auth_rate_limits RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO roles(code, name) VALUES ('READER', 'Reader'), ('LIBRARIAN', 'Librarian'), ('ADMIN', 'Admin')");
        sequence = 0;
        reader = user(RoleCode.READER);
        otherReader = user(RoleCode.READER);
        librarian = user(RoleCode.LIBRARIAN);
        otherLibrarian = user(RoleCode.LIBRARIAN);
        admin = user(RoleCode.ADMIN);
        long library = jdbc.queryForObject("INSERT INTO libraries(code, name) VALUES ('ACCESS', 'Access') RETURNING id", Long.class);
        branch = jdbc.queryForObject("""
                INSERT INTO branches(library_id, name, address)
                VALUES (?, 'Branch', '{"city":"City","street":"Street","building":"1"}') RETURNING id
                """, Long.class, library);
        jdbc.update("INSERT INTO library_rules(branch_id, valid_from) VALUES (?, timezone('UTC', clock_timestamp()) - INTERVAL '1 day')", branch);
        material = jdbc.queryForObject("INSERT INTO materials(title) VALUES ('Access material') RETURNING id", Long.class);
    }

    long user(RoleCode role) {
        int number = ++sequence;
        long id = jdbc.queryForObject("""
                INSERT INTO users(email, phone, password_hash, first_name, last_name, status)
                VALUES (?, ?, ?, 'Access', 'User', 'ACTIVE') RETURNING id
                """, Long.class, "access-" + number + "@example.com", "+7900%07d".formatted(number), passwordHash);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = CAST(? AS role_code)", id, role.name());
        return id;
    }

    long actor(int rank) {
        return switch (rank) {
            case 1 -> reader;
            case 2 -> librarian;
            case 3 -> admin;
            default -> throw new IllegalArgumentException("Anonymous has no user ID");
        };
    }

    long copy() {
        return jdbc.queryForObject("""
                INSERT INTO material_copies(material_id, branch_id, inventory_number)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, material, branch, "ACCESS-" + ++sequence);
    }

    long reservation(long owner) {
        return reservations.create(owner, new CreateReservationRequest(owner, material, copy(), branch)).id();
    }

    long loan(long owner) {
        return loans.create(librarian, new CreateLoanRequest(owner, copy(), null, branch, librarian, null)).id();
    }

    long fine(long owner) {
        return jdbc.queryForObject("INSERT INTO fines(user_id, reason, amount) VALUES (?, 'OTHER', 10) RETURNING id", Long.class, owner);
    }

    long payment(long owner) {
        return jdbc.queryForObject("""
                INSERT INTO payment_transactions(fine_id, amount, external_payment_id)
                VALUES (?, 10, ?) RETURNING id
                """, Long.class, fine(owner), "access-payment-" + owner);
    }

    long notification(long owner, String type) {
        long id = notifications.create(new CreateNotificationRequest(owner, null, null, null,
                NotificationType.valueOf(type), NotificationChannel.EMAIL, "secret-subject-marker", "secret-body-marker")).id();
        jdbc.update("UPDATE notifications SET error_message = 'secret-error-marker' WHERE id = ?", id);
        return id;
    }

    long warning(long owner) {
        return jdbc.queryForObject("""
                INSERT INTO user_warnings(user_id, created_by_user_id, reason, created_at, expires_at)
                VALUES (?, ?, 'Warning', timezone('UTC', clock_timestamp()) - INTERVAL '2 days',
                    timezone('UTC', clock_timestamp()) - INTERVAL '1 day') RETURNING id
                """, Long.class, owner, admin);
    }

    long block(long owner) {
        long id = jdbc.queryForObject("""
                INSERT INTO user_blocks(user_id, created_by_user_id, reason, blocked_at, expires_at)
                VALUES (?, ?, 'Block', timezone('UTC', clock_timestamp()) - INTERVAL '2 days',
                    timezone('UTC', clock_timestamp()) - INTERVAL '1 day') RETURNING id
                """, Long.class, owner, admin);
        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE id = ?", owner);
        return id;
    }

    Map<String, String> state() {
        Map<String, String> state = new LinkedHashMap<>();
        for (String table : List.of("users", "user_roles", "user_blocks", "user_warnings", "reservations", "loans",
                "material_copies", "fines", "payment_transactions", "notifications", "notification_preferences",
                "auth_tokens", "audit_logs", "outbox_events", "materials", "authors", "genres", "libraries",
                "branches", "library_rules", "fine_tariffs", "notification_templates")) {
            state.put(table, jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(item) ORDER BY to_jsonb(item)::text), "
                    + "'[]'::jsonb)::text FROM " + table + " item", String.class));
        }
        return state;
    }
}
