package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.library.request.ChangeLibraryRuleStatusRequest;
import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.dto.library.response.LibraryRuleResponse;
import me.ifmo.backend.entities.enums.LibraryRuleStatus;
import org.springframework.data.domain.Pageable;

public interface LibraryRuleService {

    LibraryRuleResponse create(CreateLibraryRuleRequest request);

    LibraryRuleResponse getLibraryRuleById(Long id);

    LibraryRuleResponse getActualByBranchId(Long branchId);

    LibraryRuleResponse update(Long id, UpdateLibraryRuleRequest request);

    LibraryRuleResponse changeStatus(Long id, ChangeLibraryRuleStatusRequest request);

    PageResponse<LibraryRuleResponse> search(Long branchId, LibraryRuleStatus status, Pageable pageable);
}
