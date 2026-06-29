package me.ifmo.backend.services;

import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.dto.fine.request.UpdateFineTariffRequest;
import me.ifmo.backend.dto.fine.response.FineTariffResponse;
import me.ifmo.backend.entities.enums.ViolationType;

public interface FineTariffService {

    FineTariffResponse create(CreateFineTariffRequest request);

    FineTariffResponse getFineTariffById(Long id);

    FineTariffResponse getActualByViolationType(ViolationType violationType);

    FineTariffResponse update(Long id, UpdateFineTariffRequest request);
}
