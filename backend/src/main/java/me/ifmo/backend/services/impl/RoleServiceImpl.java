package me.ifmo.backend.services.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.user.response.RoleResponse;
import me.ifmo.backend.entities.Role;
import me.ifmo.backend.entities.enums.RoleCode;
import me.ifmo.backend.exceptions.domain.ResourceNotFoundException;
import me.ifmo.backend.mappers.RoleMapper;
import me.ifmo.backend.repositories.RoleRepository;
import me.ifmo.backend.services.RoleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository repository;
    private final RoleMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Role with id '%s' not found".formatted(id)));

        return mapper.toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getByCode(RoleCode code) {
        Role role = repository.findByCode(code).orElseThrow(
                () -> new ResourceNotFoundException("Role with code '%s' not found".formatted(code)));

        return mapper.toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> search(Pageable pageable) {
        Page<RoleResponse> responses = repository.findAll(pageable).map(mapper::toResponse);
        return PageResponse.from(responses);
    }
}
