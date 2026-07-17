package me.ifmo.backend.fine.application;

import me.ifmo.backend.shared.web.response.PageResponse;
import me.ifmo.backend.fine.web.request.ChangeFineTariffStatusRequest;
import me.ifmo.backend.fine.web.request.CreateFineTariffRequest;
import me.ifmo.backend.fine.web.request.UpdateFineTariffRequest;
import me.ifmo.backend.fine.web.response.FineTariffResponse;
import me.ifmo.backend.fine.domain.enums.FineTariffStatus;
import me.ifmo.backend.fine.domain.enums.ViolationType;
import org.springframework.data.domain.Pageable;

public interface FineTariffService {

    FineTariffResponse create(CreateFineTariffRequest request);

    FineTariffResponse getFineTariffById(Long id);

    FineTariffResponse getActualByViolationType(ViolationType violationType);

    FineTariffResponse update(Long id, UpdateFineTariffRequest request);

    FineTariffResponse changeStatus(Long id, ChangeFineTariffStatusRequest request);

    PageResponse<FineTariffResponse> search(ViolationType violationType, FineTariffStatus status, Pageable pageable);
}
