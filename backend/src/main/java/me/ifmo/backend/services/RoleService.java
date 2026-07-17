package me.ifmo.backend.services;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.user.response.RoleResponse;
import me.ifmo.backend.entities.enums.RoleCode;
import org.springframework.data.domain.Pageable;

public interface RoleService {

    RoleResponse getRoleById(Long id);

    RoleResponse getByCode(RoleCode code);

    PageResponse<RoleResponse> search(Pageable pageable);
}
