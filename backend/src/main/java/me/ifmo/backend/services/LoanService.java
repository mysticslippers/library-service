package me.ifmo.backend.services;

import me.ifmo.backend.dto.circulation.request.CreateLoanRequest;
import me.ifmo.backend.dto.circulation.response.LoanResponse;

public interface LoanService {

    LoanResponse create(CreateLoanRequest request);
}
