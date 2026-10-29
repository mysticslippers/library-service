package me.ifmo.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.ifmo.backend.authentication.security.JwtService;
import me.ifmo.backend.catalog.storage.CoverObjectStorage;
import me.ifmo.backend.notification.application.NotificationDeliveryClient;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.persistence.UserRepository;
import me.ifmo.backend.user.persistence.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AccessTestApplication.class, properties = {
        "security.auth.protection.cleanup-enabled=false",
        "security.auth.protection.minimum-response-time=0ms",
        "security.auth.protection.key=test-only-access-quota-key-at-least-32-characters",
        "spring.cache.type=none",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "storage.covers.endpoint=http://127.0.0.1:1",
        "storage.covers.access-key=test-access-key",
        "storage.covers.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AccessSecurityIntegrationTest {

    private static final byte[] SIGNING_KEY = "access-tests-only-signing-key-with-64-bytes-0000000000000000000000"
            .getBytes(StandardCharsets.UTF_8);
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository roles;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;
    @MockitoBean private CoverObjectStorage storage;
    @MockitoBean private NotificationDeliveryClient delivery;
    @Autowired private AccessTestData data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("security.jwt.secret", () -> Base64.getEncoder().encodeToString(SIGNING_KEY));
    }

    @BeforeEach
    void setUp() {
        data.reset();
    }

    static Stream<Arguments> endpointRoles() {
        return AccessEndpointCatalog.endpoints().stream().flatMap(endpoint ->
                IntStream.rangeClosed(0, 3).mapToObj(rank -> Arguments.of(endpoint, rank)));
    }

    @ParameterizedTest(name = "{0}, rank={1}")
    @MethodSource("endpointRoles")
    void endpointRoleMatrix(AccessEndpoint endpoint, int rank) throws Exception {
        MockHttpServletRequestBuilder request;
        if (endpoint.method().equals("PUT") && endpoint.pattern().endsWith("/cover")) {
            request = multipart(HttpMethod.PUT, "/api" + endpoint.path())
                    .file(new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1}));
        } else {
            request = request(HttpMethod.valueOf(endpoint.method()), "/api" + endpoint.path());
        }
        long actor = rank == 0 ? data.reader : data.actor(rank);
        if (endpoint.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(endpoint.body().replace("\"ACTOR\"", Long.toString(actor)));
        }
        int expected = rank < endpoint.minimumRank() ? (rank == 0 ? 401 : 403) : endpoint.allowedStatus();
        Map<String, String> before = expected == 401 || expected == 403 ? data.state() : null;
        perform(request, rank == 0 ? null : token(actor), expected);
        if (before != null) {
            assertThat(data.state()).as("Rejected request must not change persisted state").isEqualTo(before);
        }
    }

    @Test
    void everyBusinessEndpointHasAnExplicitPolicyCase() {
        Set<String> registered = new TreeSet<>();
        Set<String> controllers = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("me.ifmo.backend.")) {
                controllers.add(handler.getBeanType().getName());
                mapping.getPatternValues().forEach(path -> mapping.getMethodsCondition().getMethods()
                        .forEach(method -> registered.add(method.name() + " " + path)));
            }
        });
        List<String> covered = AccessEndpointCatalog.endpoints().stream().map(AccessEndpoint::key).toList();
        assertThat(covered).doesNotHaveDuplicates();
        assertThat(registered).containsExactlyInAnyOrderElementsOf(covered);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> declared = new TreeSet<>();
        scanner.findCandidateComponents("me.ifmo.backend").stream()
                .map(bean -> bean.getBeanClassName())
                .filter(name -> !name.contains("$"))
                .forEach(declared::add);
        assertThat(controllers).containsExactlyInAnyOrderElementsOf(declared);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservations", "loans", "fines", "payment-transactions", "notifications"})
    void readerCanReadOwnRecordButCannotReadAnotherReadersRecord(String resource) throws Exception {
        long own = ownedRecord(resource, data.reader);
        long other = ownedRecord(resource, data.otherReader);
        perform(get("/api/" + resource + "/" + own), token(data.reader), 200);
        perform(get("/api/" + resource + "/" + other), token(data.reader), 403);
        perform(get("/api/" + resource + "/" + other), token(data.librarian), 200);
        perform(get("/api/" + resource + "/" + other), token(data.admin), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservations", "loans", "fines", "payment-transactions", "notifications"})
    void unfilteredSearchAndPaginationNeverIncludeAnotherReadersRecords(String resource) throws Exception {
        long own = ownedRecord(resource, data.reader);
        long other = ownedRecord(resource, data.otherReader);
        String path = "/api/" + resource;
        JsonNode page = body(perform(get(path).param("size", "1"), token(data.reader), 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.path("content").size()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(own);
        JsonNode next = body(perform(get(path).param("size", "1").param("page", "1"), token(data.reader), 200));
        assertThat(next.path("content").isEmpty()).isTrue();
        for (long staff : List.of(data.librarian, data.admin)) {
            JsonNode all = body(perform(get(path), token(staff), 200));
            assertThat(all.path("totalElements").asInt()).isEqualTo(2);
            assertThat(all.path("content").toString()).contains("\"id\":" + other);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"reservations", "loans", "fines", "notifications"})
    void readerCannotSelectAnotherUserInSearch(String resource) throws Exception {
        ownedRecord(resource, data.otherReader);
        Map<String, String> before = data.state();
        perform(get("/api/" + resource).param("userId", Long.toString(data.otherReader)), token(data.reader), 403);
        assertThat(data.state()).isEqualTo(before);
    }

    @Test
    void foreignPaymentCannotBeSelectedByExternalIdOrFineFilter() throws Exception {
        long payment = data.payment(data.otherReader);
        long fine = jdbc.queryForObject("SELECT fine_id FROM payment_transactions WHERE id = ?", Long.class, payment);
        perform(get("/api/payment-transactions/external/access-payment-" + data.otherReader), token(data.reader), 403);
        JsonNode page = body(perform(get("/api/payment-transactions").param("fineId", Long.toString(fine)), token(data.reader), 200));
        assertThat(page.path("totalElements").asInt()).isZero();
        perform(get("/api/payment-transactions/external/access-payment-" + data.otherReader), token(data.otherReader), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel-reservation", "renew-loan", "read-notification", "pay-fine", "create-reservation"})
    void foreignCommandsLeaveAllStateUnchanged(String command) throws Exception {
        MockHttpServletRequestBuilder request = switch (command) {
            case "cancel-reservation" -> post("/api/reservations/" + data.reservation(data.otherReader) + "/cancel-by-user")
                    .content("{\"reason\":\"Changed plans\"}");
            case "renew-loan" -> post("/api/loans/" + data.loan(data.otherReader) + "/renew").content("{}");
            case "read-notification" -> post("/api/notifications/" + data.notification(data.otherReader, "SYSTEM_MESSAGE") + "/read");
            case "pay-fine" -> post("/api/payment-transactions").content(json.writeValueAsString(
                    Map.of("fineId", data.fine(data.otherReader), "amount", 10)));
            case "create-reservation" -> post("/api/reservations").content(json.writeValueAsString(
                    Map.of("userId", data.otherReader, "materialId", data.material, "copyId", data.copy(), "branchId", data.branch)));
            default -> throw new IllegalArgumentException(command);
        };
        Map<String, String> before = data.state();
        perform(request.contentType(MediaType.APPLICATION_JSON), token(data.reader), 403);
        assertThat(data.state()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel-reservation", "renew-loan", "read-notification", "pay-fine", "create-reservation"})
    void ownersCanPerformTheirCommands(String command) throws Exception {
        MockHttpServletRequestBuilder request;
        int expected = 200;
        switch (command) {
            case "cancel-reservation" -> request = post("/api/reservations/" + data.reservation(data.reader) + "/cancel-by-user")
                    .content("{\"reason\":\"Changed plans\"}");
            case "renew-loan" -> request = post("/api/loans/" + data.loan(data.reader) + "/renew").content("{}");
            case "read-notification" -> request = post("/api/notifications/" + data.notification(data.reader, "SYSTEM_MESSAGE") + "/read");
            case "pay-fine" -> {
                request = post("/api/payment-transactions").content(json.writeValueAsString(Map.of("fineId", data.fine(data.reader), "amount", 10)));
                expected = 201;
            }
            case "create-reservation" -> {
                request = post("/api/reservations").content(json.writeValueAsString(
                        Map.of("userId", data.reader, "materialId", data.material, "copyId", data.copy(), "branchId", data.branch)));
                expected = 201;
            }
            default -> throw new IllegalArgumentException(command);
        }
        perform(request.contentType(MediaType.APPLICATION_JSON), token(data.reader), expected);
    }

    @ParameterizedTest
    @CsvSource({"update,2", "update,3", "status,2", "status,3", "assign-role,2", "assign-role,3",
            "revoke-role,2", "revoke-role,3", "block,2", "block,3", "warning,2", "warning,3",
            "cancel-warning,2", "cancel-warning,3", "expire-warning,2", "expire-warning,3",
            "cancel-block,2", "cancel-block,3", "expire-block,2", "expire-block,3"})
    void librarianCannotManageEqualOrHigherAccounts(String command, int targetRank) throws Exception {
        long target = targetRank == 2 ? data.otherLibrarian : data.admin;
        MockHttpServletRequestBuilder request = accountCommand(command, target);
        Map<String, String> before = data.state();
        perform(request.contentType(MediaType.APPLICATION_JSON), token(data.librarian), 403);
        assertThat(data.state()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "status", "assign-role", "revoke-role", "block", "warning",
            "cancel-warning", "expire-warning", "cancel-block", "expire-block"})
    void adminCanManageLowerAccounts(String command) throws Exception {
        if (command.equals("revoke-role")) {
            jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'LIBRARIAN'", data.otherReader);
        }
        MockHttpServletRequestBuilder request = accountCommand(command, data.otherReader);
        perform(request.contentType(MediaType.APPLICATION_JSON), token(data.admin),
                command.equals("block") || command.equals("warning") ? 201 : 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void librarianCannotCreateAnEqualOrHigherAccount(String role) throws Exception {
        Map<String, String> before = data.state();
        perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "email", "escalation@example.com", "phone", "+79999999999", "password", "Password123",
                "firstName", "Test", "lastName", "User", "roles", List.of(role)))), token(data.librarian), 403);
        assertThat(data.state()).isEqualTo(before);
    }

    @Test
    void issuedByCannotBeSpoofedEvenByAStaffMember() throws Exception {
        long copy = data.copy();
        Map<String, String> before = data.state();
        perform(post("/api/loans").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "userId", data.reader, "copyId", copy, "branchId", data.branch, "issuedByUserId", data.admin))),
                token(data.librarian), 403);
        assertThat(data.state()).isEqualTo(before);
    }

    @Test
    void librarySearchAcceptsEmptyAndNonemptyQueries() throws Exception {
        for (String query : List.of("", "   ", "access", "missing")) {
            JsonNode page = body(perform(get("/api/libraries").param("query", query), token(data.admin), 200));
            assertThat(page.path("totalElements").asInt()).isEqualTo(query.equals("missing") ? 0 : 1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNT_ACTIVATION", "PASSWORD_RECOVERY"})
    void authNotificationResponsesNeverContainSecrets(String type) throws Exception {
        long id = data.notification(data.reader, type);
        for (long actor : List.of(data.reader, data.librarian, data.admin)) {
            assertRedacted(perform(get("/api/notifications/" + id), token(actor), 200));
            assertRedacted(perform(post("/api/notifications/" + id + "/read"), token(actor), 200));
            JsonNode page = body(perform(get("/api/notifications").param("query", "secret"), token(actor), 200));
            assertThat(page.path("totalElements").asInt()).isZero();
        }
        perform(get("/api/notifications/" + id), token(data.otherReader), 403);
        Map<String, String> before = data.state();
        for (long actor : List.of(data.librarian, data.admin)) {
            perform(patch("/api/notifications/" + id + "/status")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"), token(actor), 422);
        }
        assertThat(data.state()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"ACCOUNT_ACTIVATION,2", "ACCOUNT_ACTIVATION,3", "PASSWORD_RECOVERY,2", "PASSWORD_RECOVERY,3"})
    void staffCannotManuallyCreateAuthenticationMessages(String type, int rank) throws Exception {
        Map<String, String> before = data.state();
        perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "userId", data.admin, "type", type, "channel", "EMAIL", "subject", "Fake activation", "body", "Fake code"))),
                token(data.actor(rank)), 422);
        assertThat(data.state()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"ACCOUNT_ACTIVATION,2", "ACCOUNT_ACTIVATION,3", "PASSWORD_RECOVERY,2", "PASSWORD_RECOVERY,3"})
    void ordinaryResendCannotRequeueAnAuthenticationMessage(String type, int rank) throws Exception {
        long id = data.notification(data.reader, type);
        jdbc.update("UPDATE notifications SET status = 'FAILED' WHERE id = ?", id);
        Map<String, String> before = data.state();
        perform(post("/api/notifications/" + id + "/resend"), token(data.actor(rank)), 422);
        assertThat(data.state()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void ordinaryNotificationCommandsRemainAvailableToStaff(int rank) throws Exception {
        String bearer = token(data.actor(rank));
        JsonNode created = body(perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("userId", data.reader, "type", "SYSTEM_MESSAGE",
                        "channel", "EMAIL", "subject", "Subject", "body", "Ordinary message"))), bearer, 201));
        long id = created.path("id").asLong();
        assertThat(created.path("body").asText()).isEqualTo("Ordinary message");
        JsonNode cancelled = body(perform(patch("/api/notifications/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"), bearer, 200));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        long failed = data.notification(data.reader, "SYSTEM_MESSAGE");
        jdbc.update("UPDATE notifications SET status = 'FAILED', attempt_count = 1 WHERE id = ?", failed);
        JsonNode resent = body(perform(post("/api/notifications/" + failed + "/resend"), bearer, 200));
        assertThat(resent.path("status").asText()).isEqualTo("PENDING");
        assertThat(resent.path("attemptCount").asInt()).isEqualTo(1);
    }

    @Test
    void preferencesIgnoreForeignUserIdAndOnlyUpdateTheAuthenticatedUser() throws Exception {
        perform(put("/api/notification-preferences").param("userId", Long.toString(data.otherReader))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "type", "SYSTEM_MESSAGE", "channel", "EMAIL", "enabled", true, "preferred", true,
                        "userId", data.otherReader))), token(data.reader), 200);
        assertThat(jdbc.queryForList("SELECT DISTINCT user_id FROM notification_preferences", Long.class)).containsExactly(data.reader);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "bad-signature", "missing-version", "old-version", "unknown-user", "invalid-subject"})
    void invalidJwtCannotReadOrChangeProtectedData(String scenario) throws Exception {
        var builder = Jwts.builder().subject(Long.toString(data.reader))
                .expiration(Date.from(Instant.now().plusSeconds(60))).claim("tokenVersion", 0L);
        switch (scenario) {
            case "expired" -> builder.expiration(Date.from(Instant.now().minusSeconds(60)));
            case "missing-version" -> builder.claim("tokenVersion", null);
            case "old-version" -> builder.claim("tokenVersion", 999L);
            case "unknown-user" -> builder.subject("99999999");
            case "invalid-subject" -> builder.subject("not-a-user-id");
            default -> { }
        }
        byte[] key = scenario.equals("bad-signature") ? "x".repeat(64).getBytes(StandardCharsets.UTF_8) : SIGNING_KEY;
        String bearer = scenario.equals("malformed") ? "not.a.jwt" : builder.signWith(Keys.hmacShaKeyFor(key)).compact();
        Map<String, String> before = data.state();
        perform(get("/api/auth/me"), bearer, 401);
        perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"), bearer, 401);
        assertThat(data.state()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLOCKED", "INACTIVE", "ARCHIVED", "PENDING_ACTIVATION", "LOGIN_LOCKED"})
    void previouslyIssuedJwtStopsWorkingWhenAccountIsDisabled(String state) throws Exception {
        String bearer = token(data.reader);
        if (state.equals("LOGIN_LOCKED")) {
            jdbc.update("UPDATE users SET locked_until = timezone('UTC', clock_timestamp()) + INTERVAL '1 day' WHERE id = ?", data.reader);
        } else {
            jdbc.update("UPDATE users SET status = CAST(? AS user_status) WHERE id = ?", state, data.reader);
        }
        Map<String, String> before = data.state();
        perform(get("/api/auth/me"), bearer, 401);
        assertThat(data.state()).isEqualTo(before);
    }

    @Test
    void passwordResetRevokesOldJwtThroughHttpAndNewLoginWorks() throws Exception {
        String old = token(data.reader);
        String email = users.findById(data.reader).orElseThrow().getEmail();
        perform(post("/api/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))), null, 200);
        String code = jdbc.queryForObject("SELECT token FROM auth_tokens WHERE user_id = ? AND type = 'PASSWORD_RESET' AND used_at IS NULL", String.class, data.reader);
        perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "token", code, "newPassword", "NewPassword123", "newPasswordConfirmation", "NewPassword123"))), null, 200);
        perform(get("/api/auth/me"), old, 401);
        MvcResult login = perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "NewPassword123"))), null, 200);
        perform(get("/api/auth/me"), body(login).path("accessToken").asText(), 200);
    }

    @Test
    void existingJwtUsesCurrentDatabaseRolesAfterRoleRevocation() throws Exception {
        String bearer = token(data.librarian);
        perform(get("/api/users"), bearer, 200);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = 'READER'", data.librarian);
        perform(delete("/api/users/" + data.librarian + "/roles/LIBRARIAN"), token(data.admin), 200);
        perform(get("/api/users"), bearer, 403);
        JsonNode profile = body(perform(get("/api/auth/me"), bearer, 200));
        assertThat(profile.path("roles").toString()).contains("READER").doesNotContain("LIBRARIAN");
    }

    @Test
    void roleClaimsCannotGrantPrivilegesMissingFromTheDatabase() throws Exception {
        String bearer = jwt.generate(users.findById(data.reader).orElseThrow(), List.of(RoleCode.ADMIN));
        perform(get("/api/users"), bearer, 403);
        perform(get("/api/roles"), bearer, 403);
        perform(get("/api/auth/me"), bearer, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIBRARIAN", "ADMIN"})
    void combinedRolesUseTheCurrentStaffPrivileges(String role) throws Exception {
        String bearer = token(data.reader);
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE code = CAST(? AS role_code)", data.reader, role);
        perform(get("/api/users"), bearer, 200);
        perform(get("/api/roles"), bearer, role.equals("ADMIN") ? 200 : 403);
        perform(get("/api/fines/" + data.fine(data.otherReader)), bearer, 200);
    }

    private void assertRedacted(MvcResult response) throws Exception {
        JsonNode result = body(response);
        assertThat(result.path("body").isNull()).isTrue();
        assertThat(result.path("subject").isNull()).isTrue();
        assertThat(result.path("errorMessage").isNull()).isTrue();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("secret-subject-marker", "secret-body-marker", "secret-error-marker");
    }

    private MockHttpServletRequestBuilder accountCommand(String command, long target) throws Exception {
        return switch (command) {
            case "update" -> patch("/api/users/" + target).content("{\"firstName\":\"Changed\"}");
            case "status" -> patch("/api/users/" + target + "/status").content("{\"status\":\"INACTIVE\"}");
            case "assign-role" -> post("/api/users/" + target + "/roles").content("{\"roleCode\":\"LIBRARIAN\"}");
            case "revoke-role" -> delete("/api/users/" + target + "/roles/READER");
            case "block" -> post("/api/user-blocks").content(json.writeValueAsString(Map.of("userId", target, "reason", "Test")));
            case "warning" -> post("/api/user-warnings").content(json.writeValueAsString(Map.of("userId", target, "reason", "Test")));
            case "cancel-warning" -> post("/api/user-warnings/" + data.warning(target) + "/cancel").content("{}");
            case "expire-warning" -> post("/api/user-warnings/" + data.warning(target) + "/expire");
            case "cancel-block" -> post("/api/user-blocks/" + data.block(target) + "/cancel").content("{\"reason\":\"Resolved\"}");
            case "expire-block" -> post("/api/user-blocks/" + data.block(target) + "/expire");
            default -> throw new IllegalArgumentException(command);
        };
    }

    private long ownedRecord(String resource, long owner) {
        return switch (resource) {
            case "reservations" -> data.reservation(owner);
            case "loans" -> data.loan(owner);
            case "fines" -> data.fine(owner);
            case "payment-transactions" -> data.payment(owner);
            case "notifications" -> data.notification(owner, "SYSTEM_MESSAGE");
            default -> throw new IllegalArgumentException(resource);
        };
    }

    private String token(long actor) {
        return jwt.generate(users.findById(actor).orElseThrow(), roles.findRoleCodesByUser_Id(actor));
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String bearer, int expected) throws Exception {
        request.servletPath("/api");
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    private JsonNode body(MvcResult response) throws Exception {
        return json.readTree(response.getResponse().getContentAsByteArray());
    }
}
