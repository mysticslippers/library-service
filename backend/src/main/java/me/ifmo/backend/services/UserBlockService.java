package me.ifmo.backend.services;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.user.request.CancelUserBlockRequest;
import me.ifmo.backend.dto.user.request.CreateUserBlockRequest;
import me.ifmo.backend.dto.user.response.UserBlockResponse;
import me.ifmo.backend.entities.enums.UserBlockStatus;
import org.springframework.data.domain.Pageable;

public interface UserBlockService {

    UserBlockResponse create(Long createdByUserId, CreateUserBlockRequest request);

    UserBlockResponse getUserBlockById(Long id);

    UserBlockResponse cancel(Long id, Long unblockedByUserId, CancelUserBlockRequest request);

    UserBlockResponse expire(Long id);

    PageResponse<UserBlockResponse> search(Long userId, Long createdByUserId, UserBlockStatus status, Pageable pageable);
}
