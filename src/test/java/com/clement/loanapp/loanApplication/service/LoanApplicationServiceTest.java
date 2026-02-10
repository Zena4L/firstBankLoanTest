package com.clement.loanapp.loanApplication.service;

import com.clement.loanapp.common.BadRequestException;
import com.clement.loanapp.common.DuplicateException;
import com.clement.loanapp.loanApplication.domain.dto.ApplicantLoanRequest;
import com.clement.loanapp.loanApplication.domain.dto.GenericMessage;
import com.clement.loanapp.loanApplication.domain.model.ApplicantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    private ApplicantLoanRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ApplicantLoanRequest(
                "John",
                "Doe",
                "john.doe@example.com",
                5000.0,
                1,
                1200.00
        );
    }

    @Test
    @DisplayName("Should create application when email not registered and amounts positive")
    void shouldCreateApplicationForValidRequest() {
        when(applicantRepository.existsByEmail(validRequest.email())).thenReturn(false);

        GenericMessage result = loanApplicationService.createLoanForApplication(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.message()).isEqualTo("Application successfully");
        verify(applicantRepository).existsByEmail(validRequest.email());
        verify(applicantRepository).save(Mockito.any());
        verifyNoMoreInteractions(applicantRepository);
    }

    @Test
    @DisplayName("Should throw DuplicateException when email already exists")
    void shouldThrowDuplicateWhenEmailExists() {
        when(applicantRepository.existsByEmail(validRequest.email())).thenReturn(true);

        assertThrows(DuplicateException.class, () ->
                loanApplicationService.createLoanForApplication(validRequest)
        );

        verify(applicantRepository).existsByEmail(validRequest.email());
        verify(applicantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw BadRequestException when monthlyIncome is non-positive")
    void shouldThrowBadRequestWhenMonthlyIncomeNonPositive() {
        ApplicantLoanRequest badIncome = new ApplicantLoanRequest(
                "John", "Doe", "john.doe@example.com", 0.0, 1, 120.00
        );
        when(applicantRepository.existsByEmail(badIncome.email())).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                loanApplicationService.createLoanForApplication(badIncome)
        );

        verify(applicantRepository).existsByEmail(badIncome.email());
        verify(applicantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw BadRequestException when loanAmount is non-positive")
    void shouldThrowBadRequestWhenLoanAmountNonPositive() {
        ApplicantLoanRequest badAmount = new ApplicantLoanRequest(
                "John", "Doe", "john.doe@example.com", 5000.0, 1, 100.00
        );
        when(applicantRepository.existsByEmail(badAmount.email())).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                loanApplicationService.createLoanForApplication(badAmount)
        );

        verify(applicantRepository).existsByEmail(badAmount.email());
        verify(applicantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should map request fields into Applicant entity before saving")
    void shouldMapFieldsIntoApplicantOnSave() {
        when(applicantRepository.existsByEmail(validRequest.email())).thenReturn(false);

        loanApplicationService.createLoanForApplication(validRequest);

        ArgumentCaptor<com.clement.loanapp.loanApplication.domain.model.Applicant> captor = ArgumentCaptor.forClass(com.clement.loanapp.loanApplication.domain.model.Applicant.class);
        verify(applicantRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getFirstName()).isEqualTo(validRequest.firstName());
        assertThat(saved.getLastName()).isEqualTo(validRequest.lastName());
        assertThat(saved.getEmail()).isEqualTo(validRequest.email());
        assertThat(saved.getMonthlyIncome()).isEqualTo(validRequest.monthlyIncome());
        assertThat(saved.getRequestLoanAmount()).isEqualTo(validRequest.loanAmount());
        assertThat(saved.getTenor()).isEqualTo(validRequest.tenor());
    }
}
