package me.ifmo.backend.audit.web;

import me.ifmo.backend.audit.application.AuditDetailsSanitizer;
import me.ifmo.backend.audit.application.impl.AuditLogServiceImpl;
import me.ifmo.backend.audit.domain.AuditLog;
import me.ifmo.backend.audit.domain.enums.AuditAction;
import me.ifmo.backend.audit.domain.enums.AuditEntityType;
import me.ifmo.backend.audit.mapper.AuditDetailsJsonMapper;
import me.ifmo.backend.audit.mapper.AuditLogMapperImpl;
import me.ifmo.backend.audit.persistence.AuditLogRepository;
import me.ifmo.backend.shared.web.GlobalExceptionHandler;
import me.ifmo.backend.user.mapper.RoleMapperImpl;
import me.ifmo.backend.user.mapper.UserMapperImpl;
import me.ifmo.backend.user.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuditLogControllerTest.Config.class)
class AuditLogControllerTest {

    private static final String SECRET = "legacy-api-secret-marker";
    @MockitoBean private AuditLogRepository repository;
    @MockitoBean private UserRepository users;
    @Autowired private AuditLogController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "unused", "ROLE_ADMIN"));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminDetailResponseFiltersLegacyDataWithoutChangingTheStoredEntity() throws Exception {
        AuditLog dirty = legacyLog();
        when(repository.findById(44L)).thenReturn(Optional.of(dirty));

        String response = mvc.perform(get("/audit-logs/44")).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.new_data.id").value(41))
                .andExpect(jsonPath("$.details.new_data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.details.new_data.password_hash").doesNotExist())
                .andExpect(jsonPath("$.details.old_data.password_hash").doesNotExist())
                .andExpect(jsonPath("$.details.assignedRole").value("LIBRARIAN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(SECRET, "passwordHash", "accessToken", "metadata");
        assertThat(dirty.getDetails().toString()).contains(SECRET);
    }

    @Test
    void adminSearchResponseFiltersEveryLegacyEntry() throws Exception {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<AuditLog>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(legacyLog())));

        String response = mvc.perform(get("/audit-logs").param("type", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details.new_data.id").value(41))
                .andExpect(jsonPath("$.content[0].details.old_data.password_hash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(SECRET, "password_hash", "accessToken");
    }

    @ParameterizedTest
    @ValueSource(strings = {"READER", "LIBRARIAN"})
    void nonAdminsCannotReadEitherAuditEndpoint(String role) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("staff", "unused", "ROLE_" + role));

        mvc.perform(get("/audit-logs/44")).andExpect(status().isForbidden());
        mvc.perform(get("/audit-logs")).andExpect(status().isForbidden());

        verifyNoInteractions(repository, users);
    }

    private AuditLog legacyLog() {
        Map<String, Object> snapshot = Map.of("id", 41, "status", "ACTIVE", "password_hash", SECRET,
                "passwordHash", SECRET, "accessToken", SECRET);
        return AuditLog.builder().id(44L).entityId(41L).type(AuditEntityType.USER).action(AuditAction.UPDATE)
                .createdAt(LocalDateTime.of(2026, 10, 23, 12, 0))
                .details(Map.of("table", "users", "operation", "UPDATE", "old_data", snapshot, "new_data", snapshot,
                        "assignedRole", "LIBRARIAN", "metadata", List.of(Map.of("token", SECRET)))).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({AuditLogController.class, AuditLogServiceImpl.class, AuditLogMapperImpl.class,
            AuditDetailsJsonMapper.class, AuditDetailsSanitizer.class, UserMapperImpl.class, RoleMapperImpl.class})
    static class Config {}
}
