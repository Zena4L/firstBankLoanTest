package com.clement.loanapp.loanApplication.domain.event;

import java.math.BigDecimal;

public record ApproveLoanEvent(String applicantEmail, BigDecimal amountRequested) {
}
