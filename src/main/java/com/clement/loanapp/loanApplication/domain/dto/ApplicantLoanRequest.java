package com.clement.loanapp.loanApplication.domain.dto;

import com.clement.loanapp.common.StringTrimmerDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ApplicantLoanRequest(
        @NotBlank(message = "FirstName is required")
        @JsonDeserialize(using = StringTrimmerDeserializer.class)
        String firstName,

        @JsonDeserialize(using = StringTrimmerDeserializer.class)
        @NotBlank(message = "LastName is required")
        String lastName,

        @JsonDeserialize(using = StringTrimmerDeserializer.class)
        @NotBlank(message = "email is required")
        @Email(message = "should be a valid email")
        String email,

        @NotNull(message = "Loan amount can't be null")
        BigDecimal loanAmount,

        @Min(1)
        @Max(12)
        int tenor,

        @NotNull(message = "Monthly Income can't be null")
        BigDecimal monthlyIncome,

        @NotNull(message = "Monthly Income can't be null")
        BigDecimal monthlyPayment

) {
}
