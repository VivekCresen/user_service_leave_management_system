package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findByUser_Id(Long userId);

    Optional<PasswordResetOtp> findByEmailIdIgnoreCase(String emailId);

    void deleteByExpiryTimeBefore(Instant now);
}
