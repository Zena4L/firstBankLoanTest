package com.clement.loanapp.loanApplication.domain.dto;

import com.clement.loanapp.loanApplication.domain.model.LoanStatus;

public record ApprovalRequest(LoanStatus status) {
}
