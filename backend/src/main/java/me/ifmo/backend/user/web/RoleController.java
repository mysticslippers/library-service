package me.ifmo.backend.user.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.response.RoleResponse;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.user.application.RoleService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Roles", description = "Read-only access to application roles")
public class RoleController {

    private final RoleService service;

    @GetMapping("/{id}")
    @Operation(summary = "Get a role by ID")
    public RoleResponse getRoleById(@PathVariable Long id) {
        return service.getRoleById(id);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get a role by code")
    public RoleResponse getByCode(@PathVariable RoleCode code) {
        return service.getByCode(code);
    }

    @GetMapping
    @Operation(summary = "List roles")
    public PageResponse<RoleResponse> search(@ParameterObject Pageable pageable) {
        return service.search(pageable);
    }
}
