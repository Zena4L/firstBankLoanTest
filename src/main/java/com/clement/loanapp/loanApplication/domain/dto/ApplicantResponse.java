package com.clement.loanapp.loanApplication.domain.dto;

import com.clement.loanapp.loanApplication.domain.model.LoanStatus;

import java.math.BigDecimal;

public record ApplicantResponse(
        String name,
        BigDecimal monthlyIncome,
        int tenor,
        String email,
        BigDecimal requestLoan,
        LoanStatus loanStatus,
        BigDecimal amountCredited


) {
}
