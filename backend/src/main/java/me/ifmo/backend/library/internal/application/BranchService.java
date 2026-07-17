package me.ifmo.backend.library.internal.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.library.internal.web.request.ChangeBranchStatusRequest;
import me.ifmo.backend.library.internal.web.request.CreateBranchRequest;
import me.ifmo.backend.library.internal.web.request.UpdateBranchRequest;
import me.ifmo.backend.library.internal.web.response.BranchResponse;
import me.ifmo.backend.library.internal.domain.enums.BranchStatus;
import org.springframework.data.domain.Pageable;

public interface BranchService {

    BranchResponse create(CreateBranchRequest request);

    BranchResponse getBranchById(Long id);

    BranchResponse update(Long id, UpdateBranchRequest request);

    BranchResponse changeStatus(Long id, ChangeBranchStatusRequest request);

    PageResponse<BranchResponse> search(Long libraryId, BranchStatus status, Pageable pageable);
}
