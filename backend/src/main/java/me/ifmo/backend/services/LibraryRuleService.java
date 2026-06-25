package me.ifmo.backend.services;

import me.ifmo.backend.dto.library.request.CreateLibraryRuleRequest;
import me.ifmo.backend.dto.library.request.UpdateLibraryRuleRequest;
import me.ifmo.backend.dto.library.response.LibraryRuleResponse;

public interface LibraryRuleService {

    LibraryRuleResponse create(CreateLibraryRuleRequest request);

    LibraryRuleResponse getLibraryRuleById(Long id);

    LibraryRuleResponse getActualByBranchId(Long branchId);

    LibraryRuleResponse update(Long id, UpdateLibraryRuleRequest request);
}
