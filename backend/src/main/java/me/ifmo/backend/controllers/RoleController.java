package me.ifmo.backend.controllers;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.response.RoleResponse;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.services.RoleService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final RoleService service;

    @GetMapping("/{id}")
    public RoleResponse getRoleById(@PathVariable Long id) {
        return service.getRoleById(id);
    }

    @GetMapping("/code/{code}")
    public RoleResponse getByCode(@PathVariable RoleCode code) {
        return service.getByCode(code);
    }

    @GetMapping
    public PageResponse<RoleResponse> search(Pageable pageable) {
        return service.search(pageable);
    }
}
