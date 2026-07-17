package me.ifmo.backend.user.application.impl;

import lombok.RequiredArgsConstructor;
import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.response.RoleResponse;
import me.ifmo.backend.user.domain.Role;
import me.ifmo.backend.user.domain.enums.RoleCode;
import me.ifmo.backend.shared.error.ResourceNotFoundException;
import me.ifmo.backend.user.mapper.RoleMapper;
import me.ifmo.backend.user.persistence.RoleRepository;
import me.ifmo.backend.user.application.RoleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository repository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Role with id '%s' not found".formatted(id)));

        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getByCode(RoleCode code) {
        Role role = repository.findByCode(code).orElseThrow(
                () -> new ResourceNotFoundException("Role with code '%s' not found".formatted(code)));

        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> search(Pageable pageable) {
        Page<RoleResponse> responses = repository.findAll(pageable).map(roleMapper::toResponse);
        return PageResponse.from(responses);
    }
}
