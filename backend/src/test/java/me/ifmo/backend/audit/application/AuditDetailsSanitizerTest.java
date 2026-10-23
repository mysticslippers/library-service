package me.ifmo.backend.audit.application;

import me.ifmo.backend.audit.domain.AuditLog;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditDetailsSanitizerTest {

    private final AuditDetailsSanitizer sanitizer = new AuditDetailsSanitizer();

    @Test
    void dropsCredentialsAndUnknownFieldsFromBothSnapshots() {
        Map<String, Object> snapshot = Map.of("id", 4L, "status", "ACTIVE", "password_hash", "secret-hash",
                "passwordHash", "secret-hash", "token", "secret-token", "email", "private@example.com",
                "future_credentials", Map.of("secret", "nested-secret"));

        var result = sanitizer.sanitize(AuditEntityType.USER, Map.of("old_data", snapshot, "new_data", snapshot,
                "operation", "UPDATE", "table", "users", "password_changed", true));

        assertThat(result).containsEntry("password_changed", true).containsEntry("operation", "UPDATE");
        assertThat(result.get("old_data")).isEqualTo(Map.of("id", 4L, "status", "ACTIVE"));
        assertThat(result.get("new_data")).isEqualTo(result.get("old_data"));
    }

    @Test
    void rejectsContainersInScalarFieldsAndFiltersRoleArrays() {
        var result = sanitizer.sanitize(AuditEntityType.USER, Map.of(
                "status", Map.of("password", "nested-secret"), "reason", List.of(Map.of("token", "secret")),
                "roles", List.of("READER", Map.of("token", "secret"), "unknown-secret-role", "ADMIN"),
                "assignedRole", "LIBRARIAN", "updated", true));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "roles", List.of("READER", "ADMIN"), "assignedRole", "LIBRARIAN", "updated", true));
    }

    @Test
    void copiesNestedDataWithoutMutatingOrRetainingMutableInput() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Moscow");
        address.put("street", null);
        address.put("secret", "must-not-survive");
        Map<String, Object> snapshot = new LinkedHashMap<>(Map.of("id", 1, "address", address));
        Map<String, Object> source = new LinkedHashMap<>(Map.of("new_data", snapshot));

        var result = sanitizer.sanitize(AuditEntityType.BRANCH, source);
        assertThat(address).containsEntry("secret", "must-not-survive");
        address.put("city", "Changed after sanitizing");
        snapshot.clear();
        source.clear();

        Map<?, ?> safeSnapshot = (Map<?, ?>) result.get("new_data");
        Map<?, ?> safeAddress = (Map<?, ?>) safeSnapshot.get("address");
        assertThat(safeAddress.get("city")).isEqualTo("Moscow");
        assertThat(safeAddress.containsKey("street")).isTrue();
        assertThat(safeAddress.get("street")).isNull();
        assertThat(safeAddress.containsKey("secret")).isFalse();
        assertThatThrownBy(safeAddress::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void removesBodiesErrorsAndTransportIdentifiersFromNotificationAudit() {
        var result = sanitizer.sanitize(AuditEntityType.NOTIFICATION, Map.of("action", "RESEND", "new_data", Map.of(
                "id", 3, "status", "UNKNOWN", "attempt_count", 1, "body", "reset-secret",
                "subject", "secret-subject", "error_message", "provider-secret", "external_message_id", "transport-secret")));

        assertThat(result).containsEntry("action", "RESEND");
        assertThat(result.get("new_data")).isEqualTo(Map.of("id", 3, "status", "UNKNOWN", "attempt_count", 1));
    }

    @Test
    void doesNotTrustTheTableNameAndHandlesMissingDetails() {
        assertThat(sanitizer.sanitize(AuditEntityType.NOTIFICATION, Map.of("table", "users", "password_changed", true)))
                .isEmpty();
        assertThat(sanitizer.sanitize(null, Map.of("status", "ACTIVE"))).isEmpty();
        assertThat(sanitizer.sanitize(AuditEntityType.USER, null)).isEmpty();
        assertThat(sanitizer.sanitize(AuditEntityType.USER, Map.of())).isEmpty();
    }

    @Test
    void copiesRolesAndNeverPrintsRawAuditDetails() {
        List<String> roles = new ArrayList<>(List.of("READER"));
        var safe = sanitizer.sanitize(AuditEntityType.USER, Map.of("roles", roles));
        roles.add("ADMIN");
        assertThat(safe.get("roles")).isEqualTo(List.of("READER"));

        AuditLog log = AuditLog.builder().id(5L).details(Map.of("password_hash", "raw-secret-marker")).build();
        assertThat(log.toString()).contains("id=5").doesNotContain("details", "raw-secret-marker", "password_hash");
    }
}
