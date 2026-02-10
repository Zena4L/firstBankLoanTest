package com.clement.loanapp.loanApplication.service;

import com.clement.loanapp.loanApplication.domain.dto.ApplicantLoanRequest;
import com.clement.loanapp.loanApplication.domain.dto.ApplicantResponse;
import com.clement.loanapp.loanApplication.domain.dto.ApprovalRequest;
import com.clement.loanapp.loanApplication.domain.dto.GenericMessage;
import com.clement.loanapp.loanApplication.domain.model.LoanStatus;
import org.springframework.data.domain.Page;

public interface LoanService {
    GenericMessage createLoanForApplication(ApplicantLoanRequest request);

    Page<ApplicantResponse> getAllApplicants(int page, int size);

    LoanStatus approveLoan(ApprovalRequest request, String applicantId);
}
