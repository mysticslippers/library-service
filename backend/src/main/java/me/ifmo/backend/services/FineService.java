package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.request.FineSearchRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;
import org.springframework.data.domain.Pageable;

public interface FineService {

    FineResponse create(CreateFineRequest request);

    FineResponse getFineById(Long id);

    FineResponse cancel(Long id, CancelFineRequest request);

    FineResponse markPaid(Long id);

    PageResponse<FineResponse> search(FineSearchRequest request, Pageable pageable);
}
