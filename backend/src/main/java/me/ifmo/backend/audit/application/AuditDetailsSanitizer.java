package me.ifmo.backend.audit.application;

import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class AuditDetailsSanitizer {

    private static final Set<String> OPERATIONS = Set.of("INSERT", "UPDATE", "DELETE");
    private static final Set<String> ROLES = Set.of("READER", "LIBRARIAN", "ADMIN");
    private static final Set<String> ADDRESS_FIELDS = Set.of("city", "street", "building");

    public Map<String, Object> sanitize(AuditEntityType type, Map<String, Object> details) {
        if (type == null || details == null)
            return Map.of();

        Map<String, Object> safe = pickScalars(details, manualFields(type));
        if (details.get("operation") instanceof String operation && OPERATIONS.contains(operation))
            safe.put("operation", details.get("operation"));
        if (table(type).equals(details.get("table")))
            safe.put("table", table(type));

        for (String key : Set.of("old_data", "new_data")) {
            if (details.get(key) instanceof Map<?, ?> snapshot) {
                Map<String, Object> safeSnapshot = pickScalars(snapshot, snapshotFields(type));
                if (type == AuditEntityType.BRANCH && snapshot.get("address") instanceof Map<?, ?> address)
                    safeSnapshot.put("address", Collections.unmodifiableMap(pickScalars(address, ADDRESS_FIELDS)));
                safe.put(key, Collections.unmodifiableMap(safeSnapshot));
            }
        }
        if (type == AuditEntityType.USER) {
            if (details.get("password_changed") instanceof Boolean flag)
                safe.put("password_changed", flag);
            if (details.get("roles") instanceof Collection<?> roles)
                safe.put("roles", roles.stream().filter(value -> value instanceof String && ROLES.contains(value)).toList());
        }
        return Collections.unmodifiableMap(safe);
    }

    private Map<String, Object> pickScalars(Map<?, ?> source, Set<String> allowed) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : allowed) {
            Object value = source.get(key);
            if (source.containsKey(key) && (value == null || value instanceof String
                    || value instanceof Number || value instanceof Boolean))
                safe.put(key, value);
        }
        return safe;
    }

    private Set<String> manualFields(AuditEntityType type) {
        return switch (type) {
            case USER -> Set.of("status", "reason", "updated", "assignedRole", "revokedRole");
            case USER_BLOCK -> Set.of("userId", "reason");
            case USER_WARNING -> Set.of("userId", "reason");
            case NOTIFICATION -> Set.of("action", "processed", "createdAt");
            case NOTIFICATION_TEMPLATE -> Set.of("type", "channel");
            case NOTIFICATION_PREFERENCE -> Set.of("type", "channel", "enabled", "preferred");
            default -> Set.of();
        };
    }

    private Set<String> snapshotFields(AuditEntityType type) {
        return switch (type) {
            case USER -> Set.of("id", "status", "registered_at", "activated_at", "last_login_at", "failed_login_attempts", "locked_until", "home_branch_id", "token_version");
            case ROLE -> Set.of("id", "code", "name", "description");
            case LIBRARY -> Set.of("id", "code", "name", "status", "created_at");
            case BRANCH -> Set.of("id", "library_id", "name", "status", "created_at");
            case LIBRARY_RULE -> Set.of("id", "branch_id", "max_active_reservations", "max_active_loans", "reservation_ttl_days", "default_loan_days", "renewal_allowed", "max_renewal_count", "renewal_period_days", "reservation_allowed", "status", "valid_from", "valid_to");
            case GENRE -> Set.of("id", "code", "name", "status", "created_at");
            case AUTHOR -> Set.of("id", "first_name", "last_name", "middle_name", "status", "created_at");
            case MATERIAL -> Set.of("id", "material_type", "title", "description", "isbn", "publication_year", "publisher", "language", "status", "created_at", "updated_at");
            case MATERIAL_COPY -> Set.of("id", "material_id", "branch_id", "inventory_number", "status", "shelf_location", "created_at", "updated_at");
            case RESERVATION -> Set.of("id", "user_id", "material_id", "copy_id", "branch_id", "status", "created_at", "expires_at", "ready_at", "cancelled_at", "cancellation_reason");
            case LOAN -> Set.of("id", "user_id", "copy_id", "reservation_id", "branch_id", "issued_by_user_id", "loaned_at", "due_at", "returned_at", "renewal_count", "status");
            case FINE_TARIFF -> Set.of("id", "violation_type", "amount_per_day", "fixed_amount", "max_amount", "status", "valid_from", "valid_to");
            case FINE -> Set.of("id", "user_id", "loan_id", "copy_id", "tariff_id", "reason", "amount", "status", "created_at", "paid_at", "cancelled_at", "cancellation_reason");
            case PAYMENT_TRANSACTION -> Set.of("id", "fine_id", "amount", "status", "created_at", "updated_at");
            case NOTIFICATION -> Set.of("id", "user_id", "reservation_id", "loan_id", "fine_id", "type", "channel", "status", "created_at", "sent_at", "read_at", "attempt_count", "sending_started_at", "version");
            case NOTIFICATION_TEMPLATE -> Set.of("id", "type", "channel", "status", "created_at", "updated_at");
            case NOTIFICATION_PREFERENCE -> Set.of("id", "user_id", "type", "channel", "enabled", "preferred", "created_at", "updated_at");
            case USER_BLOCK -> Set.of("id", "user_id", "created_by_user_id", "reason", "blocked_at", "expires_at", "unblocked_by_user_id", "unblock_reason", "unblocked_at", "status");
            case USER_WARNING -> Set.of("id", "user_id", "created_by_user_id", "reason", "comment", "created_at", "expires_at", "status");
        };
    }

    private String table(AuditEntityType type) {
        return switch (type) {
            case USER -> "users";
            case ROLE -> "roles";
            case LIBRARY -> "libraries";
            case BRANCH -> "branches";
            case LIBRARY_RULE -> "library_rules";
            case GENRE -> "genres";
            case AUTHOR -> "authors";
            case MATERIAL -> "materials";
            case MATERIAL_COPY -> "material_copies";
            case RESERVATION -> "reservations";
            case LOAN -> "loans";
            case FINE_TARIFF -> "fine_tariffs";
            case FINE -> "fines";
            case PAYMENT_TRANSACTION -> "payment_transactions";
            case NOTIFICATION -> "notifications";
            case NOTIFICATION_TEMPLATE -> "notification_templates";
            case NOTIFICATION_PREFERENCE -> "notification_preferences";
            case USER_BLOCK -> "user_blocks";
            case USER_WARNING -> "user_warnings";
        };
    }
}
