package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.exception.InvalidOtpException;
import com.cresensolutions.userservice.model.PasswordResetOtp;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.PasswordResetOtpRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class OtpServiceImpl implements OtpService {

    private final PasswordResetOtpRepository otpRepository;
    private final long expirationMinutes;

    public OtpServiceImpl(
            PasswordResetOtpRepository otpRepository,
            @Value("${security.otp.expiration-minutes:10}") long expirationMinutes
    ) {
        this.otpRepository = otpRepository;
        this.expirationMinutes = expirationMinutes;
    }

    @Override
    public String createOtp(UserAccount user) {
        String normalizedEmail = normalize(user == null ? null : user.getEmail());
        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        Instant expiryTime = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);

        PasswordResetOtp otpRecord = findOtpRecord(user, normalizedEmail)
                .map(existingOtp -> {
                    existingOtp.setUser(user);
                    existingOtp.setEmailId(normalizedEmail);
                    existingOtp.setOtpCode(otp);
                    existingOtp.setExpiryTime(expiryTime);
                    return existingOtp;
                })
                .orElseGet(() -> new PasswordResetOtp(user, otp, expiryTime));

        otpRepository.save(otpRecord);

        return otp;
    }

    @Override
    @Transactional(readOnly = true)
    public void validateOtp(UserAccount user, String otp) {
        String normalizedEmail = normalize(user == null ? null : user.getEmail());
        PasswordResetOtp record = findOtpRecord(user, normalizedEmail)
                .orElseThrow(() -> new InvalidOtpException("OTP not found. Please request a new OTP."));

        if (record.getExpiryTime() == null || !record.getExpiryTime().isAfter(Instant.now())) {
            throw new InvalidOtpException("OTP has expired. Please request a new OTP.");
        }

        if (!record.getOtpCode().equals(otp)) {
            throw new InvalidOtpException("Invalid OTP. Please try again.");
        }
    }

    @Override
    public void clearOtp(UserAccount user) {
        String normalizedEmail = normalize(user == null ? null : user.getEmail());
        findOtpRecord(user, normalizedEmail).ifPresent(otpRepository::delete);
    }

    @Override
    public void cleanupExpiredOtps() {
        otpRepository.deleteByExpiryTimeBefore(Instant.now());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private Optional<PasswordResetOtp> findOtpRecord(UserAccount user, String normalizedEmail) {
        if (user != null && user.getId() != null) {
            Optional<PasswordResetOtp> otpByUserId = otpRepository.findByUser_Id(user.getId());
            if (otpByUserId.isPresent()) {
                return otpByUserId;
            }
        }

        return otpRepository.findByEmailIdIgnoreCase(normalizedEmail);
    }
}
