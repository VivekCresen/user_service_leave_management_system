package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.LoginRequest;
import com.cresensolutions.userservice.dto.LoginResponse;
import com.cresensolutions.userservice.dto.OtpRequest;
import com.cresensolutions.userservice.dto.OtpResponse;
import com.cresensolutions.userservice.dto.OtpValidationRequest;
import com.cresensolutions.userservice.dto.ResetPasswordWithOtpRequest;
import com.cresensolutions.userservice.dto.RoleSummaryResponse;
import com.cresensolutions.userservice.exception.AuthenticationFailedException;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.messaging.UserEventPublisher;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.service.Impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationAuditService authenticationAuditService;
    @Mock private OtpService otpService;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserEventPublisher eventPublisher;

    private AuthServiceImpl authService;
    private UserAccount activeUser;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                roleRepository, userRepository, jwtService,
                authenticationAuditService, otpService, emailService,
                passwordEncoder, eventPublisher);

        activeUser = new UserAccount();
        activeUser.setUsername("alice");
        activeUser.setEmail("alice@cresensolutions.com");
        activeUser.setFullName("Alice Smith");
        activeUser.setPassword("$2a$10$hashedpassword");
        activeUser.setActive(true);
        activeUser.assignRole(new Role(1L, "EMPLOYEE", "EMPLOYEE"));
    }

    @Test
    void login_validCredentials_returnsLoginResponse() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Secret1!", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("alice", base64("Secret1!")));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void login_userNotFound_throwsAuthenticationFailedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", base64("pass"))))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(eventPublisher).publishLoginFailed(anyString());
    }

    @Test
    void login_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", base64("Secret1!"))))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void login_wrongPassword_throwsAuthenticationFailedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", base64("WrongPass1!"))))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(eventPublisher).publishLoginFailed(anyString());
    }

    @Test
    void login_invalidBase64Password_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "not-base64!!!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void login_successfulLogin_updatesLastLogin() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");

        authService.login(new LoginRequest("alice", base64("Secret1!")));

        assertThat(activeUser.getLastLogin()).isNotNull();
    }

    // ── requestPasswordResetOtp ───────────────────────────────────────────────

    @Test
    void requestPasswordResetOtp_knownEmail_returnsOtpResponse() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));
        when(otpService.createOtp(activeUser)).thenReturn("654321");

        OtpResponse response = authService.requestPasswordResetOtp(
                new OtpRequest("alice@cresensolutions.com"));

        assertThat(response.message()).contains("OTP");
    }

    @Test
    void requestPasswordResetOtp_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestPasswordResetOtp(
                new OtpRequest("unknown@cresensolutions.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requestPasswordResetOtp_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.requestPasswordResetOtp(
                new OtpRequest("alice@cresensolutions.com")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void requestPasswordResetOtp_nullEmail_treatedAsEmpty() {
        when(userRepository.findByEmailIdIgnoreCase("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestPasswordResetOtp(new OtpRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── verifyPasswordResetOtp ────────────────────────────────────────────────

    @Test
    void verifyPasswordResetOtp_validOtp_returnsSuccessMessage() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));
        doNothing().when(otpService).validateOtp(activeUser, "123456");

        OtpResponse response = authService.verifyPasswordResetOtp(
                new OtpValidationRequest("alice@cresensolutions.com", "123456"));

        assertThat(response.message()).contains("verified");
    }

    @Test
    void verifyPasswordResetOtp_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyPasswordResetOtp(
                new OtpValidationRequest("nobody@cresensolutions.com", "000000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyPasswordResetOtp_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.verifyPasswordResetOtp(
                new OtpValidationRequest("alice@cresensolutions.com", "123456")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_validRequest_updatesPasswordAndReturnsResponse() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));
        doNothing().when(otpService).validateOtp(eq(activeUser), anyString());
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$10$newHash");
        when(jwtService.generateToken(activeUser)).thenReturn("new-jwt");

        LoginResponse response = authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456",
                        base64("NewPass1!")));

        assertThat(response.token()).isEqualTo("new-jwt");
        verify(otpService).clearOtp(activeUser);
    }

    @Test
    void resetPassword_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("nobody@cresensolutions.com", "123456",
                        base64("NewPass1!"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456",
                        base64("NewPass1!"))))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void resetPassword_weakPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456",
                        base64("weakpass"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_tooShortPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456",
                        base64("Ab1!"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

    @Test
    void resetPassword_tooLongPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        String longPass = "Aa1!" + "x".repeat(260);
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456",
                        base64(longPass))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

    @Test
    void resetPassword_blankPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        String emptyBase64 = Base64.getEncoder().encodeToString("".getBytes());
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456", emptyBase64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void resetPassword_invalidBase64Password_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@cresensolutions.com", "123456",
                        "not-base64!!!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    // ── fetchRoleSummary ──────────────────────────────────────────────────────

    @Test
    void fetchRoleSummary_returnsListPerRole() {
        Role admin = new Role(1L, "ADMIN", "ADMIN");
        Role employee = new Role(2L, "EMPLOYEE", "EMPLOYEE");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(admin, employee));
        when(userRepository.findRoleAssignments()).thenReturn(List.of());

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).roleName()).isEqualTo("ADMIN");
        assertThat(summaries.get(1).roleName()).isEqualTo("EMPLOYEE");
    }

    @Test
    void fetchRoleSummary_withAssignments_countsUsersPerRole() {
        Role employee = new Role(1L, "EMPLOYEE", "EMPLOYEE");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(employee));
        when(userRepository.findRoleAssignments()).thenReturn(List.of(
                roleView("alice", "EMPLOYEE"),
                roleView("bob", "EMPLOYEE")));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(2);
    }

    @Test
    void fetchRoleSummary_withNullOrBlankAssignments_skipsEntry() {
        Role employee = new Role(1L, "EMPLOYEE", "EMPLOYEE");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(employee));
        when(userRepository.findRoleAssignments()).thenReturn(List.of(
                roleView(null, "EMPLOYEE"),
                roleView("  ", "EMPLOYEE"),
                roleView("alice", null),
                roleView("alice", "  ")));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(0);
    }

    @Test
    void fetchRoleSummary_roleWithAliases_mapsCorrectly() {
        Role manager = new Role(1L, "MANAGER", "MANAGER");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(manager));
        when(userRepository.findRoleAssignments()).thenReturn(List.of(
                roleView("alice", "MANAGER")));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(1);
        assertThat(summaries.get(0).usernames()).containsExactly("alice");
    }

    @Test
    void fetchRoleSummary_emptyRoles_returnsEmptyList() {
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
        when(userRepository.findRoleAssignments()).thenReturn(List.of());

        assertThat(authService.fetchRoleSummary()).isEmpty();
    }

    // ── post-commit callbacks ─────────────────────────────────────────────────

    @Test
    void login_withActiveSynchronization_publishesLoginSuccessAfterCommit() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            authService.login(new LoginRequest("alice", base64("Secret1!")));
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations().forEach(s -> s.afterCommit());
            verify(eventPublisher).publishLoginSuccess("alice");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void requestPasswordResetOtp_withActiveSynchronization_publishesOtpAfterCommit() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));
        when(otpService.createOtp(activeUser)).thenReturn("123456");

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            authService.requestPasswordResetOtp(new OtpRequest("alice@cresensolutions.com"));
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations().forEach(s -> s.afterCommit());
            verify(eventPublisher).publishOtpRequested(
                    eq("alice@cresensolutions.com"), anyString(), eq("123456"));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resetPassword_withActiveSynchronization_publishesPasswordResetAfterCommit() {
        when(userRepository.findByEmailIdIgnoreCase("alice@cresensolutions.com"))
                .thenReturn(Optional.of(activeUser));
        doNothing().when(otpService).validateOtp(any(), anyString());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(jwtService.generateToken(activeUser)).thenReturn("jwt");

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            authService.resetPassword(new ResetPasswordWithOtpRequest(
                    "alice@cresensolutions.com", "123456", base64("NewPass1!")));
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations().forEach(s -> s.afterCommit());
            verify(eventPublisher).publishPasswordReset("alice@cresensolutions.com");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    private UserRepository.RoleAssignmentView roleView(String username, String role) {
        return new UserRepository.RoleAssignmentView() {
            @Override public String getUsername() { return username; }
            @Override public String getRole()     { return role; }
        };
    }
}
