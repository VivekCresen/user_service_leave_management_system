package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.*;
import com.cresensolutions.userservice.exception.AuthenticationFailedException;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private AuthServiceImpl authService;

    private UserAccount activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new UserAccount();
        activeUser.setUsername("alice");
        activeUser.setEmail("alice@example.com");
        activeUser.setPassword("$2a$10$hashedpassword");
        activeUser.setActive(true);
        activeUser.assignRole(new Role(1L, "EMPLOYEE", "EMPLOYEE"));
    }

    @Test
    void login_validCredentials_returnsLoginResponse() {
        String encoded = Base64.getEncoder().encodeToString("Secret1!".getBytes());
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Secret1!", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("alice", encoded));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void login_userNotFound_throwsAuthenticationFailedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", base64("pass"))))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(authenticationAuditService).logLoginFailure(anyString());
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
        verify(authenticationAuditService).logLoginFailure(anyString());
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
    void requestPasswordResetOtp_knownEmail_returnsOtpResponse() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        when(otpService.createOtp(activeUser)).thenReturn("654321");

        OtpResponse response = authService.requestPasswordResetOtp(new OtpRequest("alice@example.com"));

        assertThat(response.message()).contains("OTP");
    }

    @Test
    void requestPasswordResetOtp_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestPasswordResetOtp(new OtpRequest("unknown@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requestPasswordResetOtp_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.requestPasswordResetOtp(new OtpRequest("alice@example.com")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void verifyPasswordResetOtp_validOtp_returnsSuccessMessage() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        doNothing().when(otpService).validateOtp(activeUser, "123456");

        OtpResponse response = authService.verifyPasswordResetOtp(
                new OtpValidationRequest("alice@example.com", "123456"));

        assertThat(response.message()).contains("verified");
    }

    @Test
    void verifyPasswordResetOtp_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyPasswordResetOtp(
                new OtpValidationRequest("nobody@example.com", "000000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_validRequest_updatesPasswordAndReturnsResponse() {
        String newPass = base64("NewPass1!");
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        doNothing().when(otpService).validateOtp(eq(activeUser), anyString());
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$10$newHash");
        when(jwtService.generateToken(activeUser)).thenReturn("new-jwt");

        LoginResponse response = authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", newPass));

        assertThat(response.token()).isEqualTo("new-jwt");
        verify(otpService).clearOtp(activeUser);
    }

    @Test
    void resetPassword_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmailIdIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("nobody@example.com", "123456", base64("NewPass1!"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetPassword_weakPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", base64("weakpass"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_tooShortPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", base64("Ab1!"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

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

        UserRepository.RoleAssignmentView view1 = roleAssignmentView("alice", "EMPLOYEE");
        UserRepository.RoleAssignmentView view2 = roleAssignmentView("bob", "EMPLOYEE");
        when(userRepository.findRoleAssignments()).thenReturn(List.of(view1, view2));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(2);
    }

    @Test
    void fetchRoleSummary_withNullUsernameAssignment_skipsEntry() {
        Role employee = new Role(1L, "EMPLOYEE", "EMPLOYEE");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(employee));

        UserRepository.RoleAssignmentView nullUsername = roleAssignmentView(null, "EMPLOYEE");
        UserRepository.RoleAssignmentView blankUsername = roleAssignmentView("  ", "EMPLOYEE");
        UserRepository.RoleAssignmentView nullRole = roleAssignmentView("alice", null);
        when(userRepository.findRoleAssignments()).thenReturn(List.of(nullUsername, blankUsername, nullRole));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(0);
    }

    @Test
    void verifyPasswordResetOtp_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.verifyPasswordResetOtp(
                new OtpValidationRequest("alice@example.com", "123456")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void resetPassword_inactiveUser_throwsAuthenticationFailedException() {
        activeUser.setActive(false);
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", base64("NewPass1!"))))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void resetPassword_blankPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        String emptyBase64 = Base64.getEncoder().encodeToString("".getBytes());
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", emptyBase64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void login_invalidBase64_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "not-base64!!!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void fetchRoleSummary_roleWithAliases_mapsCorrectly() {
        Role manager = new Role(1L, "MANAGER", "MANAGER");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(manager));

        UserRepository.RoleAssignmentView view = roleAssignmentView("alice", "MANAGER");
        when(userRepository.findRoleAssignments()).thenReturn(List.of(view));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(1);
        assertThat(summaries.get(0).usernames()).containsExactly("alice");
    }

    @Test
    void resetPassword_invalidBase64Password_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", "not-base64!!!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void resetPassword_tooLongPassword_throwsIllegalArgumentException() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        String longPass = "Aa1!" + "x".repeat(260);
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", base64(longPass))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

    @Test
    void requestPasswordResetOtp_nullEmail_treatedAsEmpty() {
        when(userRepository.findByEmailIdIgnoreCase("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestPasswordResetOtp(new OtpRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fetchRoleSummary_blankRoleAssignment_skipsEntry() {
        Role employee = new Role(1L, "EMPLOYEE", "EMPLOYEE");
        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(employee));
        UserRepository.RoleAssignmentView blankRole = roleAssignmentView("alice", "  ");
        when(userRepository.findRoleAssignments()).thenReturn(List.of(blankRole));

        List<RoleSummaryResponse> summaries = authService.fetchRoleSummary();

        assertThat(summaries.get(0).userCount()).isEqualTo(0);
    }

    @Test
    void login_nullUsername_normalize_usesEmpty() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("", ""))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(null, base64("pass"))))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(authenticationAuditService).logLoginFailure("");
    }

    @Test
    void resetPassword_nullDecodedPassword_throwsPasswordRequired() {
        when(userRepository.findByEmailIdIgnoreCase("alice@example.com"))
                .thenReturn(Optional.of(activeUser));

        String emptyBase64 = Base64.getEncoder().encodeToString("".getBytes());
        assertThatThrownBy(() -> authService.resetPassword(
                new ResetPasswordWithOtpRequest("alice@example.com", "123456", emptyBase64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void login_runAfterCommit_withActiveSynchronization_registersCallback() {

        String encoded = Base64.getEncoder().encodeToString("Secret1!".getBytes());
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Secret1!", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            LoginResponse response = authService.login(new LoginRequest("alice", encoded));
            assertThat(response.token()).isEqualTo("jwt-token");
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(s -> s.afterCommit());
            verify(authenticationAuditService).logLoginSuccess("alice");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    private UserRepository.RoleAssignmentView roleAssignmentView(String username, String role) {
        return new UserRepository.RoleAssignmentView() {
            @Override public String getUsername() { return username; }
            @Override public String getRole() { return role; }
        };
    }
}
