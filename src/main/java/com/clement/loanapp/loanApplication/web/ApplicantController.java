package com.clement.loanapp.loanApplication.web;

import com.clement.loanapp.loanApplication.domain.dto.ApplicantLoanRequest;
import com.clement.loanapp.loanApplication.domain.dto.ApplicantResponse;
import com.clement.loanapp.loanApplication.domain.dto.ApprovalRequest;
import com.clement.loanapp.loanApplication.domain.dto.GenericMessage;
import com.clement.loanapp.loanApplication.domain.model.LoanStatus;
import com.clement.loanapp.loanApplication.service.LoanService;
import jakarta.validation.Valid;
import jdk.jfr.BooleanFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan")
@Slf4j
@RequiredArgsConstructor
public class ApplicantController {
    private final LoanService loanService;


    @PostMapping("/apply")
    public GenericMessage createLoanForApplication(
            @RequestBody @Valid ApplicantLoanRequest request
    ) {
        return loanService.createLoanForApplication(request);
    }

    @GetMapping("/applicants")
    public Page<ApplicantResponse> getAllApplicant(@RequestParam(required = false, defaultValue = "0") int page,
                                                   @RequestParam(required = false, defaultValue = "100") int size){
        return loanService.getAllApplicants(page,size);
    }

    @PostMapping("/approve/{applicantId}")
    public LoanStatus approveLoan(@RequestBody ApprovalRequest request,
                                  @PathVariable String applicantId){
       return loanService.approveLoan(request,applicantId);
    }


}
