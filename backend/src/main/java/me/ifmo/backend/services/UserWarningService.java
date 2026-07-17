package me.ifmo.backend.services;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserWarningRequest;
import me.ifmo.backend.dto.user.request.CreateUserWarningRequest;
import me.ifmo.backend.dto.user.response.UserWarningResponse;
import me.ifmo.backend.entities.enums.UserWarningStatus;
import org.springframework.data.domain.Pageable;

public interface UserWarningService {

    UserWarningResponse create(Long createdByUserId, CreateUserWarningRequest request);

    UserWarningResponse getUserWarningById(Long id);

    UserWarningResponse cancel(Long id, CancelUserWarningRequest request);

    UserWarningResponse expire(Long id);

    PageResponse<UserWarningResponse> search(Long userId, Long createdByUserId, UserWarningStatus status, Pageable pageable);
}
