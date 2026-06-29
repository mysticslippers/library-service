package me.ifmo.backend.services;

import me.ifmo.backend.dto.common.response.PageResponse;
import me.ifmo.backend.dto.fine.request.ChangeFineTariffStatusRequest;
import me.ifmo.backend.dto.fine.request.CreateFineTariffRequest;
import me.ifmo.backend.dto.fine.request.UpdateFineTariffRequest;
import me.ifmo.backend.dto.fine.response.FineTariffResponse;
import me.ifmo.backend.entities.enums.FineTariffStatus;
import me.ifmo.backend.entities.enums.ViolationType;
import org.springframework.data.domain.Pageable;

public interface FineTariffService {

    FineTariffResponse create(CreateFineTariffRequest request);

    FineTariffResponse getFineTariffById(Long id);

    FineTariffResponse getActualByViolationType(ViolationType violationType);

    FineTariffResponse update(Long id, UpdateFineTariffRequest request);

    FineTariffResponse changeStatus(Long id, ChangeFineTariffStatusRequest request);

    PageResponse<FineTariffResponse> search(ViolationType violationType, FineTariffStatus status, Pageable pageable);
}
