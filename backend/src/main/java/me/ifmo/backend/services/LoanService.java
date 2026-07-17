package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.request.LoanSearchRequest;
import me.ifmo.backend.dto.circulation.request.RenewLoanRequest;
import me.ifmo.backend.dto.circulation.request.ReturnLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;
import me.ifmo.backend.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface LoanService {

    LoanResponse create(Long actorUserId, CreateLoanRequest request);

    LoanResponse getLoanById(Long actorUserId, Long id);

    LoanResponse returnLoan(Long actorUserId, Long id, ReturnLoanRequest request);

    LoanResponse renew(Long actorUserId, Long id, RenewLoanRequest request);

    LoanResponse markOverdue(Long actorUserId, Long id);

    LoanResponse markLost(Long actorUserId, Long id);

    PageResponse<LoanResponse> search(Long actorUserId, LoanSearchRequest request, Pageable pageable);
}
