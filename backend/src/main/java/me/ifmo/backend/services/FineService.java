package me.ifmo.backend.services;

import me.ifmo.backend.dto.fine.request.CancelFineRequest;
import me.ifmo.backend.dto.fine.request.CreateFineRequest;
import me.ifmo.backend.dto.fine.response.FineResponse;

public interface FineService {

    FineResponse create(CreateFineRequest request);

    FineResponse getFineById(Long id);

    FineResponse cancel(Long id, CancelFineRequest request);
}
