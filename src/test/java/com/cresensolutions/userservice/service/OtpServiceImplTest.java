package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.exception.InvalidOtpException;
import com.cresensolutions.userservice.model.PasswordResetOtp;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.PasswordResetOtpRepository;
import com.cresensolutions.userservice.service.Impl.OtpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private PasswordResetOtpRepository otpRepository;

    private OtpServiceImpl otpService;
    private UserAccount userWithId;
    private UserAccount userWithoutId;

    @BeforeEach
    void setUp() throws Exception {
        otpService = new OtpServiceImpl(otpRepository, 10L);

        userWithId = new UserAccount();
        userWithId.setUsername("testuser");
        userWithId.setEmail("test@cresensolutions.com");
        var idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(userWithId, 1L);

        userWithoutId = new UserAccount();
        userWithoutId.setUsername("testuser");
        userWithoutId.setEmail("test@cresensolutions.com");
    }

    @Test
    void createOtp_newRecord_savesAndReturns6DigitOtp() {
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(userWithId);

        assertThat(otp).matches("\\d{6}");
        verify(otpRepository).save(any(PasswordResetOtp.class));
    }

    @Test
    void createOtp_existingRecord_foundById_updatesAndReturnsNewOtp() {
        PasswordResetOtp existing = new PasswordResetOtp(userWithId, "111111", Instant.now().plusSeconds(60));
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(existing));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(userWithId);

        assertThat(otp).matches("\\d{6}");
        assertThat(existing.getOtpCode()).isEqualTo(otp);
        verify(otpRepository).save(existing);
    }

    @Test
    void createOtp_userWithNoId_fallsBackToEmailLookup_newRecord() {
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(userWithoutId);

        assertThat(otp).matches("\\d{6}");
        verify(otpRepository, never()).findByUser_Id(any());
        verify(otpRepository).save(any(PasswordResetOtp.class));
    }

    @Test
    void createOtp_userWithNoId_fallsBackToEmailLookup_existingRecord() {
        PasswordResetOtp existing = new PasswordResetOtp("test@cresensolutions.com", "111111", Instant.now().plusSeconds(60));
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.of(existing));
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(userWithoutId);

        assertThat(otp).matches("\\d{6}");
        verify(otpRepository).save(existing);
    }

    @Test
    void createOtp_userHasId_notFoundById_fallsBackToEmail() {
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(userWithId);

        assertThat(otp).matches("\\d{6}");
        verify(otpRepository).findByUser_Id(1L);
        verify(otpRepository).findByEmailIdIgnoreCase("test@cresensolutions.com");
    }

    @Test
    void createOtp_nullUser_usesEmptyEmailFallback() {
        when(otpRepository.findByEmailIdIgnoreCase("")).thenReturn(Optional.empty());
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(null);

        assertThat(otp).matches("\\d{6}");
        verify(otpRepository, never()).findByUser_Id(any());
    }

    @Test
    void createOtp_emailWithWhitespace_isTrimmedAndLowercased() {
        userWithoutId.setEmail("  TEST@cresensolutions.com  ");
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.createOtp(userWithoutId);

        verify(otpRepository).findByEmailIdIgnoreCase("test@cresensolutions.com");
    }

    @Test
    void validateOtp_validOtp_doesNotThrow() {
        PasswordResetOtp record = new PasswordResetOtp(userWithId, "123456", Instant.now().plusSeconds(300));
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(record));

        otpService.validateOtp(userWithId, "123456");
    }

    @Test
    void validateOtp_noRecord_throwsInvalidOtpException() {
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.validateOtp(userWithId, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("OTP not found");
    }

    @Test
    void validateOtp_expiredOtp_throwsInvalidOtpException() {
        PasswordResetOtp record = new PasswordResetOtp(userWithId, "123456", Instant.now().minusSeconds(1));
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otpService.validateOtp(userWithId, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateOtp_nullExpiryTime_throwsInvalidOtpException() {
        PasswordResetOtp record = new PasswordResetOtp("test@cresensolutions.com", "123456", null);
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otpService.validateOtp(userWithId, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateOtp_wrongCode_throwsInvalidOtpException() {
        PasswordResetOtp record = new PasswordResetOtp(userWithId, "999999", Instant.now().plusSeconds(300));
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otpService.validateOtp(userWithId, "000000"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid OTP");
    }

    @Test
    void validateOtp_nullUser_throwsInvalidOtpException() {
        when(otpRepository.findByEmailIdIgnoreCase("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.validateOtp(null, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("OTP not found");
    }

    @Test
    void validateOtp_userWithNoId_fallsBackToEmail_throwsNotFound() {
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.validateOtp(userWithoutId, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("OTP not found");
    }


    @Test
    void clearOtp_existingRecord_deletesIt() {
        PasswordResetOtp record = new PasswordResetOtp(userWithId, "123456", Instant.now().plusSeconds(300));
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(record));

        otpService.clearOtp(userWithId);

        verify(otpRepository).delete(record);
    }

    @Test
    void clearOtp_noRecord_doesNothing() {
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());

        otpService.clearOtp(userWithId);

        verify(otpRepository, never()).delete(any());
    }

    @Test
    void clearOtp_nullUser_doesNothing() {
        when(otpRepository.findByEmailIdIgnoreCase("")).thenReturn(Optional.empty());

        otpService.clearOtp(null);

        verify(otpRepository, never()).delete(any());
    }

    @Test
    void clearOtp_userWithNoId_fallsBackToEmail_doesNothing() {
        when(otpRepository.findByEmailIdIgnoreCase("test@cresensolutions.com")).thenReturn(Optional.empty());

        otpService.clearOtp(userWithoutId);

        verify(otpRepository, never()).delete(any());
    }


    @Test
    void cleanupExpiredOtps_delegatesToRepository() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);

        otpService.cleanupExpiredOtps();

        verify(otpRepository).deleteByExpiryTimeBefore(captor.capture());
        assertThat(captor.getValue()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void createOtp_userWithNullEmail_normalizesToEmpty() {
        userWithoutId.setEmail(null);
        when(otpRepository.findByEmailIdIgnoreCase("")).thenReturn(Optional.empty());
        when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.createOtp(userWithoutId);

        assertThat(otp).matches("\\d{6}");
        verify(otpRepository).findByEmailIdIgnoreCase("");
    }

    @Test
    void validateOtp_correctCode_doesNotThrow() {
        PasswordResetOtp record = new PasswordResetOtp(userWithId, "999999", Instant.now().plusSeconds(300));
        when(otpRepository.findByUser_Id(1L)).thenReturn(Optional.of(record));

        otpService.validateOtp(userWithId, "999999");
    }
}
