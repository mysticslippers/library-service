package me.ifmo.backend.library.internal.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.internal.web.request.ChangeLibraryRuleStatusRequest;
import me.ifmo.backend.library.internal.web.request.CreateLibraryRuleRequest;
import me.ifmo.backend.library.internal.web.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.library.internal.web.response.LibraryRuleResponse;
import me.ifmo.backend.library.internal.domain.enums.LibraryRuleStatus;
import org.springframework.data.domain.Pageable;

public interface LibraryRuleService {

    LibraryRuleResponse create(CreateLibraryRuleRequest request);

    LibraryRuleResponse getLibraryRuleById(Long id);

    LibraryRuleResponse getActualByBranchId(Long branchId);

    LibraryRuleResponse update(Long id, UpdateLibraryRuleRequest request);

    LibraryRuleResponse changeStatus(Long id, ChangeLibraryRuleStatusRequest request);

    PageResponse<LibraryRuleResponse> search(Long branchId, LibraryRuleStatus status, Pageable pageable);
}
