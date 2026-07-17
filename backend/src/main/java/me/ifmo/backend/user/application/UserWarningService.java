package me.ifmo.backend.user.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.request.CancelUserWarningRequest;
import me.ifmo.backend.user.web.request.CreateUserWarningRequest;
import me.ifmo.backend.user.web.response.UserWarningResponse;
import me.ifmo.backend.user.domain.enums.UserWarningStatus;
import org.springframework.data.domain.Pageable;

public interface UserWarningService {

    UserWarningResponse create(Long createdByUserId, CreateUserWarningRequest request);

    UserWarningResponse getUserWarningById(Long id);

    UserWarningResponse cancel(Long id, CancelUserWarningRequest request);

    UserWarningResponse expire(Long id);

    PageResponse<UserWarningResponse> search(Long userId, Long createdByUserId, UserWarningStatus status, Pageable pageable);
}
