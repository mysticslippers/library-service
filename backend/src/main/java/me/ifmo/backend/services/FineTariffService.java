package me.ifmo.backend.services;

import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.dto.fine.response.FineTariffResponse;

public interface FineTariffService {

    FineTariffResponse create(CreateFineTariffRequest request);

    FineTariffResponse getFineTariffById(Long id);
}
