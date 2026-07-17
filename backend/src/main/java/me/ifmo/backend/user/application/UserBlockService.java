package me.ifmo.backend.user.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.user.web.request.CancelUserBlockRequest;
import me.ifmo.backend.user.web.request.CreateUserBlockRequest;
import me.ifmo.backend.user.web.response.UserBlockResponse;
import me.ifmo.backend.user.domain.enums.UserBlockStatus;
import org.springframework.data.domain.Pageable;

public interface UserBlockService {

    UserBlockResponse create(Long createdByUserId, CreateUserBlockRequest request);

    UserBlockResponse getUserBlockById(Long id);

    UserBlockResponse cancel(Long id, Long unblockedByUserId, CancelUserBlockRequest request);

    UserBlockResponse expire(Long id);

    PageResponse<UserBlockResponse> search(Long userId, Long createdByUserId, UserBlockStatus status, Pageable pageable);
}
