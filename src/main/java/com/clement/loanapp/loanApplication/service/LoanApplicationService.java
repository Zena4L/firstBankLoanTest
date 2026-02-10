package com.clement.loanapp.loanApplication.service;

import com.clement.loanapp.common.DuplicateException;
import com.clement.loanapp.common.NotFoundException;
import com.clement.loanapp.loanApplication.domain.dto.ApplicantLoanRequest;
import com.clement.loanapp.loanApplication.domain.dto.ApplicantResponse;
import com.clement.loanapp.loanApplication.domain.dto.ApprovalRequest;
import com.clement.loanapp.loanApplication.domain.dto.GenericMessage;
import com.clement.loanapp.loanApplication.domain.event.ApproveLoanEvent;
import com.clement.loanapp.loanApplication.domain.model.Applicant;
import com.clement.loanapp.loanApplication.domain.model.ApplicantRepository;
import com.clement.loanapp.loanApplication.domain.model.Loan;
import com.clement.loanapp.loanApplication.domain.model.LoanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationService implements LoanService {
    private final ApplicantRepository applicantRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public GenericMessage createLoanForApplication(ApplicantLoanRequest request) {

        if (applicantRepository.existsByEmail(request.email())) {
            throw new DuplicateException("You are an already registered applicant");
        }


        if (checkCredit(request.monthlyIncome(), request.monthlyPayment())) {
            throw new UnsupportedOperationException("To qualify for a loan, " +
                    "your monthly income must be three(3) times more than your monthly installments");
        }

        var applicant = Applicant.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .monthlyIncome(request.monthlyIncome())
                .requestLoanAmount(request.loanAmount())
                .tenor(request.tenor())
                .loan(null)
                .monthlyPayment(request.monthlyPayment())
                .build();

        applicantRepository.save(applicant);
        eventPublisher.publishEvent(new ApproveLoanEvent(request.email(), request.loanAmount()));

        return new GenericMessage("Application successfully");
    }

    @Override
    public Page<ApplicantResponse> getAllApplicants(int page, int size) {

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").ascending()
        );

        Page<Applicant> allApplicants = applicantRepository.findAll(pageRequest);

        return allApplicants.map(applicant ->
                new ApplicantResponse(
                        applicant.getFirstName() + " " + applicant.getLastName(),
                        applicant.getMonthlyIncome(),
                        applicant.getTenor(),
                        applicant.getEmail(),
                        applicant.getRequestLoanAmount(),
                        applicant.getStatus(),
                        applicant.getBalance()
                )
        );
    }

    @Override
    public LoanStatus approveLoan(ApprovalRequest request, String applicantId) {

        Applicant applicant = applicantRepository
                .findApplicantById(UUID.fromString(applicantId))
                .orElseThrow(() -> new NotFoundException("Applicant not found"));

        if (applicant.getStatus() == LoanStatus.APPROVED) {
            return LoanStatus.APPROVED;
        }

        processLoanApproval(applicant, applicant.getRequestLoanAmount());

        applicantRepository.save(applicant);
        return applicant.getStatus();
    }


    @Retryable
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loanApproval(ApproveLoanEvent event) {

        Applicant applicant = applicantRepository
                .findByEmail(event.applicantEmail())
                .orElseThrow(() ->
                        new NotFoundException(
                                "Applicant not found for email: " + event.applicantEmail()
                        )
                );

        if (applicant.getStatus() == LoanStatus.APPROVED) {
            return;
        }

        processLoanApproval(applicant, event.amountRequested());

        applicantRepository.save(applicant);
    }

    private void processLoanApproval(Applicant applicant, BigDecimal amountRequested) {

        boolean isEligible = evaluateEligibility(applicant);

        if (isEligible) {
            Loan loan = createLoan(amountRequested);

            applicant.setStatus(LoanStatus.APPROVED);
            applicant.setBalance(amountRequested);
            applicant.setLoan(loan);
            applicant.setCreditCheck(true);

        } else {
            applicant.setStatus(LoanStatus.REJECTED);
            applicant.setCreditCheck(false);
        }
    }


    private boolean checkCredit(BigDecimal monthlyIncome, BigDecimal monthlyPayment) {
        if (monthlyIncome == null || monthlyPayment == null) {
            return false;
        }

        BigDecimal threshold = monthlyPayment.multiply(BigDecimal.valueOf(3));
        return monthlyIncome.compareTo(threshold) > 0;
    }


    private boolean evaluateEligibility(Applicant applicant) {
        return checkCredit(
                applicant.getMonthlyIncome(),
                applicant.getMonthlyPayment()
        );
    }

    private Loan createLoan(BigDecimal amount) {
        return Loan.builder()
                .credited(amount)
                .dueDate(
                        ZonedDateTime.now()
                                .plusMonths(12)
                                .toInstant()
                )
                .build();
    }


}
