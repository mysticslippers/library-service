package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.request.RenewLoanRequest;
import me.ifmo.backend.dto.circulation.request.ReturnLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;

public interface LoanService {

    LoanResponse create(CreateLoanRequest request);

    LoanResponse getLoanById(Long id);

    LoanResponse returnLoan(Long id, ReturnLoanRequest request);

    LoanResponse renew(Long id, RenewLoanRequest request);

    LoanResponse markOverdue(Long id);

    LoanResponse markLost(Long id);
}
