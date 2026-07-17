package me.ifmo.backend.services;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.request.FineSearchRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;
import org.springframework.data.domain.Pageable;

public interface FineService {

    FineResponse create(Long actorUserId, CreateFineRequest request);

    FineResponse getFineById(Long actorUserId, Long id);

    FineResponse cancel(Long actorUserId, Long id, CancelFineRequest request);

    FineResponse markPaid(Long actorUserId, Long id);

    PageResponse<FineResponse> search(Long actorUserId, FineSearchRequest request, Pageable pageable);
}
