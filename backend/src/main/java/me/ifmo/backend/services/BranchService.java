package me.ifmo.backend.services;

import me.ifmo.backend.dto.library.request.ChangeBranchStatusRequest;
import me.ifmo.backend.dto.library.request.CreateBranchRequest;
import me.ifmo.backend.dto.library.request.UpdateBranchRequest;
import me.ifmo.backend.dto.library.response.BranchResponse;

public interface BranchService {

    BranchResponse create(CreateBranchRequest request);

    BranchResponse getBranchById(Long id);

    BranchResponse update(Long id, UpdateBranchRequest request);

    BranchResponse changeStatus(Long id, ChangeBranchStatusRequest request);
}
