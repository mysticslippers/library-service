package me.ifmo.backend.fine.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.CancelFineRequest;
import me.ifmo.backend.fine.web.request.CreateFineRequest;
import me.ifmo.backend.fine.web.request.FineSearchRequest;
import me.ifmo.backend.fine.web.response.FineResponse;
import org.springframework.data.domain.Pageable;

public interface FineService {

    FineResponse create(Long actorUserId, CreateFineRequest request);

    FineResponse getFineById(Long actorUserId, Long id);

    FineResponse cancel(Long actorUserId, Long id, CancelFineRequest request);

    FineResponse markPaid(Long actorUserId, Long id);

    PageResponse<FineResponse> search(Long actorUserId, FineSearchRequest request, Pageable pageable);
}
