package me.ifmo.backend.user.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.response.RoleResponse;
import me.ifmo.backend.user.domain.enums.RoleCode;
import org.springframework.data.domain.Pageable;

public interface RoleService {

    RoleResponse getRoleById(Long id);

    RoleResponse getByCode(RoleCode code);

    PageResponse<RoleResponse> search(Pageable pageable);
}
