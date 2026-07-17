package me.ifmo.backend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.audit.request.AuditLogSearchRequest;
import me.ifmo.backend.dto.audit.response.AuditLogResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.services.AuditLogService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService service;

    @GetMapping("/{id}")
    public AuditLogResponse getAuditLogById(@PathVariable Long id) {
        return service.getAuditLogById(id);
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(@Valid @ModelAttribute AuditLogSearchRequest request, Pageable pageable) {
        return service.search(request, pageable);
    }
}