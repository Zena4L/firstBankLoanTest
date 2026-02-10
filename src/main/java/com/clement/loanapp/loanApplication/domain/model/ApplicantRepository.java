package com.clement.loanapp.loanApplication.domain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ApplicantRepository extends JpaRepository<Applicant, UUID> {

    boolean existsByEmail(String email);

    Optional<Applicant> findByEmail(String email);

    @Query("SELECT ap FROM Applicant  ap WHERE ap.id = :id")
    Optional<Applicant> findApplicantById(UUID id);
}
