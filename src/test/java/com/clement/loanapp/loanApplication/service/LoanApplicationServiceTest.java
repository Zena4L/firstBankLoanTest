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
import com.clement.loanapp.loanApplication.domain.model.LoanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanApplicationService Tests")
class LoanApplicationServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    private ApplicantLoanRequest validRequest;
    private Applicant testApplicant;

    @BeforeEach
    void setUp() {
        validRequest = new ApplicantLoanRequest(
                "John",
                "Doe",
                "john.doe@example.com",
                new BigDecimal("10000.00"),  // loanAmount
                12,  // tenor
                new BigDecimal("5000.00"),  // monthlyIncome
                new BigDecimal("1000.00")  // monthlyPayment
        );

        testApplicant = Applicant.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .monthlyIncome(new BigDecimal("5000.00"))
                .requestLoanAmount(new BigDecimal("10000.00"))
                .monthlyPayment(new BigDecimal("1000.00"))
                .tenor(12)
                .status(LoanStatus.DRAFT)
                .creditCheck(false)
                .build();
    }

    @Nested
    @DisplayName("createLoanForApplication Tests")
    class CreateLoanForApplicationTests {

        @Test
        @DisplayName("Should create loan application successfully when all validations pass")
        void shouldCreateLoanApplicationSuccessfully() {
            // Arrange
            when(applicantRepository.existsByEmail(anyString())).thenReturn(false);
            when(applicantRepository.save(any(Applicant.class))).thenReturn(testApplicant);

            // Act
            GenericMessage result = loanApplicationService.createLoanForApplication(validRequest);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.message()).isEqualTo("Application successfully");

            ArgumentCaptor<Applicant> applicantCaptor = ArgumentCaptor.forClass(Applicant.class);
            verify(applicantRepository).save(applicantCaptor.capture());
            Applicant savedApplicant = applicantCaptor.getValue();

            assertThat(savedApplicant.getFirstName()).isEqualTo("John");
            assertThat(savedApplicant.getLastName()).isEqualTo("Doe");
            assertThat(savedApplicant.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(savedApplicant.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(savedApplicant.getRequestLoanAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));

            ArgumentCaptor<ApproveLoanEvent> eventCaptor = ArgumentCaptor.forClass(ApproveLoanEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            ApproveLoanEvent publishedEvent = eventCaptor.getValue();

            assertThat(publishedEvent.applicantEmail()).isEqualTo("john.doe@example.com");
            assertThat(publishedEvent.amountRequested()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("Should throw DuplicateException when email already exists")
        void shouldThrowDuplicateExceptionWhenEmailExists() {
            // Arrange
            when(applicantRepository.existsByEmail(anyString())).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> loanApplicationService.createLoanForApplication(validRequest))
                    .isInstanceOf(DuplicateException.class)
                    .hasMessage("You are an already registered applicant");

            verify(applicantRepository, never()).save(any(Applicant.class));
            verify(eventPublisher, never()).publishEvent(any(ApproveLoanEvent.class));
        }

        @Test
        @DisplayName("Should throw UnsupportedOperationException when monthly income is insufficient")
        void shouldThrowExceptionWhenMonthlyIncomeIsInsufficient() {
            // Arrange
            ApplicantLoanRequest insufficientIncomeRequest = new ApplicantLoanRequest(
                    "Jane",
                    "Smith",
                    "jane.smith@example.com",
                    new BigDecimal("10000.00"),  // loanAmount
                    12,  // tenor
                    new BigDecimal("2000.00"),  // monthlyIncome (insufficient)
                    new BigDecimal("1000.00")  // monthlyPayment
            );
            when(applicantRepository.existsByEmail(anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> loanApplicationService.createLoanForApplication(insufficientIncomeRequest))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("To qualify for a loan, your monthly income must be three(3) times more than your monthly installments");

            verify(applicantRepository, never()).save(any(Applicant.class));
            verify(eventPublisher, never()).publishEvent(any(ApproveLoanEvent.class));
        }

        @Test
        @DisplayName("Should create application when monthly income is exactly 3 times monthly payment")
        void shouldCreateApplicationWhenIncomeIsExactlyThreeTimesPayment() {
            // Arrange
            ApplicantLoanRequest exactThresholdRequest = new ApplicantLoanRequest(
                    "Jane",
                    "Smith",
                    "jane.smith@example.com",
                    new BigDecimal("10000.00"),  // loanAmount
                    12,  // tenor
                    new BigDecimal("3001.00"),  // monthlyIncome (just over 3x monthly payment)
                    new BigDecimal("1000.00")  // monthlyPayment
            );
            when(applicantRepository.existsByEmail(anyString())).thenReturn(false);
            when(applicantRepository.save(any(Applicant.class))).thenReturn(testApplicant);

            // Act
            GenericMessage result = loanApplicationService.createLoanForApplication(exactThresholdRequest);

            // Assert
            assertThat(result).isNotNull();
            verify(applicantRepository).save(any(Applicant.class));
            verify(eventPublisher).publishEvent(any(ApproveLoanEvent.class));
        }
    }

    @Nested
    @DisplayName("getAllApplicants Tests")
    class GetAllApplicantsTests {

        @Test
        @DisplayName("Should return paginated list of applicants")
        void shouldReturnPaginatedApplicants() {
            // Arrange
            Applicant applicant1 = Applicant.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .monthlyIncome(new BigDecimal("5000"))
                    .requestLoanAmount(new BigDecimal("10000"))
                    .tenor(12)
                    .status(LoanStatus.APPROVED)
                    .balance(new BigDecimal("10000"))
                    .build();

            Applicant applicant2 = Applicant.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .email("jane@example.com")
                    .monthlyIncome(new BigDecimal("6000"))
                    .requestLoanAmount(new BigDecimal("15000"))
                    .tenor(6)
                    .status(LoanStatus.PENDING)
                    .balance(new BigDecimal("15000"))
                    .build();

            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("createdAt").ascending());
            Page<Applicant> applicantPage = new PageImpl<>(Arrays.asList(applicant1, applicant2), pageRequest, 2);

            when(applicantRepository.findAll(any(PageRequest.class))).thenReturn(applicantPage);

            // Act
            Page<ApplicantResponse> result = loanApplicationService.getAllApplicants(0, 10);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);

            ApplicantResponse response1 = result.getContent().get(0);
            assertThat(response1.name()).isEqualTo("John Doe");
            assertThat(response1.email()).isEqualTo("john@example.com");
            assertThat(response1.loanStatus()).isEqualTo(LoanStatus.APPROVED);

            ApplicantResponse response2 = result.getContent().get(1);
            assertThat(response2.name()).isEqualTo("Jane Smith");
            assertThat(response2.email()).isEqualTo("jane@example.com");
            assertThat(response2.loanStatus()).isEqualTo(LoanStatus.PENDING);

            verify(applicantRepository).findAll(any(PageRequest.class));
        }

        @Test
        @DisplayName("Should return empty page when no applicants exist")
        void shouldReturnEmptyPageWhenNoApplicants() {
            // Arrange
            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("createdAt").ascending());
            Page<Applicant> emptyPage = new PageImpl<>(java.util.Collections.emptyList(), pageRequest, 0);

            when(applicantRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

            // Act
            Page<ApplicantResponse> result = loanApplicationService.getAllApplicants(0, 10);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("approveLoan Tests")
    class ApproveLoanTests {

        @Test
        @DisplayName("Should approve loan when applicant is eligible")
        void shouldApproveLoanWhenEligible() {
            // Arrange
            UUID applicantId = UUID.randomUUID();
            testApplicant.setId(applicantId);
            testApplicant.setStatus(LoanStatus.DRAFT);

            ApprovalRequest approvalRequest = new ApprovalRequest(LoanStatus.APPROVED);

            when(applicantRepository.findApplicantById(applicantId)).thenReturn(Optional.of(testApplicant));
            when(applicantRepository.save(any(Applicant.class))).thenReturn(testApplicant);

            // Act
            LoanStatus result = loanApplicationService.approveLoan(approvalRequest, applicantId.toString());

            // Assert
            assertThat(result).isEqualTo(LoanStatus.APPROVED);

            ArgumentCaptor<Applicant> applicantCaptor = ArgumentCaptor.forClass(Applicant.class);
            verify(applicantRepository).save(applicantCaptor.capture());
            Applicant savedApplicant = applicantCaptor.getValue();

            assertThat(savedApplicant.getStatus()).isEqualTo(LoanStatus.APPROVED);
            assertThat(savedApplicant.getBalance()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(savedApplicant.getLoan()).isNotNull();
            assertThat(savedApplicant.getCreditCheck()).isTrue();
        }

        @Test
        @DisplayName("Should reject loan when applicant is not eligible")
        void shouldRejectLoanWhenNotEligible() {
            // Arrange
            UUID applicantId = UUID.randomUUID();
            Applicant ineligibleApplicant = Applicant.builder()
                    .id(applicantId)
                    .firstName("Jane")
                    .lastName("Doe")
                    .email("jane.doe@example.com")
                    .monthlyIncome(new BigDecimal("2000.00"))  // Insufficient income
                    .requestLoanAmount(new BigDecimal("10000.00"))
                    .monthlyPayment(new BigDecimal("1000.00"))
                    .tenor(12)
                    .status(LoanStatus.DRAFT)
                    .creditCheck(false)
                    .build();

            ApprovalRequest approvalRequest = new ApprovalRequest(LoanStatus.APPROVED);

            when(applicantRepository.findApplicantById(applicantId)).thenReturn(Optional.of(ineligibleApplicant));
            when(applicantRepository.save(any(Applicant.class))).thenReturn(ineligibleApplicant);

            // Act
            LoanStatus result = loanApplicationService.approveLoan(approvalRequest, applicantId.toString());

            // Assert
            assertThat(result).isEqualTo(LoanStatus.REJECTED);

            ArgumentCaptor<Applicant> applicantCaptor = ArgumentCaptor.forClass(Applicant.class);
            verify(applicantRepository).save(applicantCaptor.capture());
            Applicant savedApplicant = applicantCaptor.getValue();

            assertThat(savedApplicant.getStatus()).isEqualTo(LoanStatus.REJECTED);
            assertThat(savedApplicant.getCreditCheck()).isFalse();
            assertThat(savedApplicant.getLoan()).isNull();
        }

        @Test
        @DisplayName("Should return APPROVED when loan is already approved")
        void shouldReturnApprovedWhenAlreadyApproved() {
            // Arrange
            UUID applicantId = UUID.randomUUID();
            testApplicant.setId(applicantId);
            testApplicant.setStatus(LoanStatus.APPROVED);

            ApprovalRequest approvalRequest = new ApprovalRequest(LoanStatus.APPROVED);

            when(applicantRepository.findApplicantById(applicantId)).thenReturn(Optional.of(testApplicant));

            // Act
            LoanStatus result = loanApplicationService.approveLoan(approvalRequest, applicantId.toString());

            // Assert
            assertThat(result).isEqualTo(LoanStatus.APPROVED);
            verify(applicantRepository, never()).save(any(Applicant.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when applicant does not exist")
        void shouldThrowNotFoundExceptionWhenApplicantDoesNotExist() {
            // Arrange
            UUID applicantId = UUID.randomUUID();
            ApprovalRequest approvalRequest = new ApprovalRequest(LoanStatus.APPROVED);

            when(applicantRepository.findApplicantById(applicantId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> loanApplicationService.approveLoan(approvalRequest, applicantId.toString()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Applicant not found");

            verify(applicantRepository, never()).save(any(Applicant.class));
        }
    }

    @Nested
    @DisplayName("loanApproval (Event Listener) Tests")
    class LoanApprovalEventListenerTests {

        @Test
        @DisplayName("Should process loan approval event for eligible applicant")
        void shouldProcessLoanApprovalEventForEligibleApplicant() {
            // Arrange
            ApproveLoanEvent event = new ApproveLoanEvent("john.doe@example.com", new BigDecimal("10000.00"));

            when(applicantRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(testApplicant));
            when(applicantRepository.save(any(Applicant.class))).thenReturn(testApplicant);

            // Act
            loanApplicationService.loanApproval(event);

            // Assert
            ArgumentCaptor<Applicant> applicantCaptor = ArgumentCaptor.forClass(Applicant.class);
            verify(applicantRepository).save(applicantCaptor.capture());
            Applicant savedApplicant = applicantCaptor.getValue();

            assertThat(savedApplicant.getStatus()).isEqualTo(LoanStatus.APPROVED);
            assertThat(savedApplicant.getBalance()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(savedApplicant.getLoan()).isNotNull();
            assertThat(savedApplicant.getLoan().getCredited()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(savedApplicant.getLoan().getDueDate()).isNotNull();
            assertThat(savedApplicant.getCreditCheck()).isTrue();
        }

        @Test
        @DisplayName("Should reject loan in event listener when applicant is not eligible")
        void shouldRejectLoanInEventListenerWhenNotEligible() {
            // Arrange
            Applicant ineligibleApplicant = Applicant.builder()
                    .id(UUID.randomUUID())
                    .firstName("Jane")
                    .lastName("Doe")
                    .email("jane.doe@example.com")
                    .monthlyIncome(new BigDecimal("2000.00"))
                    .requestLoanAmount(new BigDecimal("10000.00"))
                    .monthlyPayment(new BigDecimal("1000.00"))
                    .tenor(12)
                    .status(LoanStatus.DRAFT)
                    .creditCheck(false)
                    .build();

            ApproveLoanEvent event = new ApproveLoanEvent("jane.doe@example.com", new BigDecimal("10000.00"));

            when(applicantRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(ineligibleApplicant));
            when(applicantRepository.save(any(Applicant.class))).thenReturn(ineligibleApplicant);

            // Act
            loanApplicationService.loanApproval(event);

            // Assert
            ArgumentCaptor<Applicant> applicantCaptor = ArgumentCaptor.forClass(Applicant.class);
            verify(applicantRepository).save(applicantCaptor.capture());
            Applicant savedApplicant = applicantCaptor.getValue();

            assertThat(savedApplicant.getStatus()).isEqualTo(LoanStatus.REJECTED);
            assertThat(savedApplicant.getCreditCheck()).isFalse();
            assertThat(savedApplicant.getLoan()).isNull();
        }

        @Test
        @DisplayName("Should not process event when loan is already approved")
        void shouldNotProcessEventWhenAlreadyApproved() {
            // Arrange
            testApplicant.setStatus(LoanStatus.APPROVED);
            ApproveLoanEvent event = new ApproveLoanEvent("john.doe@example.com", new BigDecimal("10000.00"));

            when(applicantRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(testApplicant));

            // Act
            loanApplicationService.loanApproval(event);

            // Assert
            verify(applicantRepository, never()).save(any(Applicant.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when applicant email not found in event listener")
        void shouldThrowNotFoundExceptionWhenEmailNotFound() {
            // Arrange
            ApproveLoanEvent event = new ApproveLoanEvent("unknown@example.com", new BigDecimal("10000.00"));

            when(applicantRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> loanApplicationService.loanApproval(event))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Applicant not found for email: unknown@example.com");

            verify(applicantRepository, never()).save(any(Applicant.class));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null monthly income gracefully")
        void shouldHandleNullMonthlyIncome() {
            // Arrange
            ApplicantLoanRequest requestWithNullIncome = new ApplicantLoanRequest(
                    "John",
                    "Doe",
                    "john@example.com",
                    new BigDecimal("10000.00"),  // loanAmount
                    12,  // tenor
                    null,  // null monthly income
                    new BigDecimal("1000.00")  // monthlyPayment
            );

            when(applicantRepository.existsByEmail(anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> loanApplicationService.createLoanForApplication(requestWithNullIncome))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should handle null monthly payment gracefully")
        void shouldHandleNullMonthlyPayment() {
            // Arrange
            ApplicantLoanRequest requestWithNullPayment = new ApplicantLoanRequest(
                    "John",
                    "Doe",
                    "john@example.com",
                    new BigDecimal("10000.00"),  // loanAmount
                    12,  // tenor
                    new BigDecimal("5000.00"),  // monthlyIncome
                    null  // null monthly payment
            );

            when(applicantRepository.existsByEmail(anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> loanApplicationService.createLoanForApplication(requestWithNullPayment))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should verify loan due date is set to 12 months from now")
        void shouldVerifyLoanDueDateIsSet() {
            // Arrange
            UUID applicantId = UUID.randomUUID();
            testApplicant.setId(applicantId);
            testApplicant.setStatus(LoanStatus.DRAFT);

            ApprovalRequest approvalRequest = new ApprovalRequest(LoanStatus.APPROVED);

            when(applicantRepository.findApplicantById(applicantId)).thenReturn(Optional.of(testApplicant));
            when(applicantRepository.save(any(Applicant.class))).thenReturn(testApplicant);

            // Act
            loanApplicationService.approveLoan(approvalRequest, applicantId.toString());

            // Assert
            ArgumentCaptor<Applicant> applicantCaptor = ArgumentCaptor.forClass(Applicant.class);
            verify(applicantRepository).save(applicantCaptor.capture());
            Applicant savedApplicant = applicantCaptor.getValue();

            assertThat(savedApplicant.getLoan()).isNotNull();
            assertThat(savedApplicant.getLoan().getDueDate()).isNotNull();

            // Verify the due date is approximately 12 months from now
            Instant now = Instant.now();
            Instant dueDate = savedApplicant.getLoan().getDueDate();
            assertThat(dueDate).isAfter(now);
        }
    }
}

