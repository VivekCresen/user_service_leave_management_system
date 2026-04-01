package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.LoginRequest;
import com.cresensolutions.userservice.dto.LoginResponse;
import com.cresensolutions.userservice.dto.OtpRequest;
import com.cresensolutions.userservice.dto.OtpResponse;
import com.cresensolutions.userservice.dto.OtpValidationRequest;
import com.cresensolutions.userservice.dto.ResetPasswordWithOtpRequest;
import com.cresensolutions.userservice.exception.AuthenticationFailedException;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Role ADMIN_ROLE = new Role(1L, "Administrator", "ADMIN");
    private static final Role MANAGER_ROLE = new Role(2L, "Manager", "MANAGER");
    private static final Role EMPLOYEE_ROLE = new Role(3L, "Employee", "EMPLOYEE");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationAuditService authenticationAuditService;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserAccount createUser(String username, String email, String password, String roleName) {
        UserAccount user = new UserAccount(username, email, username, password, roleName);
        user.assignRole(toRole(roleName));
        return user;
    }

    private UserAccount createUser(String username, String email, String password, String roleName, boolean active) {
        UserAccount user = createUser(username, email, password, roleName);
        user.setActive(active);
        return user;
    }

    @Test
    void shouldLoginWithUsername() {
        UserAccount user = createUser("vivekadmin", "vivek.chavda@cresensolutions.com", "encoded-Password@123", "ADMIN");
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "encoded-Password@123")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.generateToken(any(UserAccount.class)))
                .thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("vivekadmin", "UGFzc3dvcmRAMTIz"));

        assertEquals("vivekadmin", response.username());
        assertEquals("ADMIN", response.role());
        assertEquals(true, response.active());
        assertEquals("jwt-token", response.token());
        assertEquals("Login successful", response.message());
    }

    @Test
    void shouldLoginWithEmail() {
        UserAccount user = createUser("vivekmanager", "viveksinhchavda@gmail.com", "encoded-manager123", "MANAGER");
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("manager123", "encoded-manager123")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.generateToken(any(UserAccount.class)))
                .thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("viveksinhchavda@gmail.com", "bWFuYWdlcjEyMw=="));

        assertEquals("vivekmanager", response.username());
        assertEquals("MANAGER", response.role());
        assertEquals(true, response.active());
    }

    @Test
    void shouldNormalizeEmailLoginBeforeLookup() {
        UserAccount user = createUser("vivekmanager", "viveksinhchavda@gmail.com", "encoded-manager123", "MANAGER");
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("viveksinhchavda@gmail.com", "viveksinhchavda@gmail.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("manager123", "encoded-manager123")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.generateToken(any(UserAccount.class))).thenReturn("jwt-token");

        authService.login(new LoginRequest(" VivekSinhChavda@Gmail.com ", "bWFuYWdlcjEyMw=="));

        verify(userRepository).findByUserNameIgnoreCaseOrEmailIdIgnoreCase(
                "viveksinhchavda@gmail.com",
                "viveksinhchavda@gmail.com"
        );
        verify(authenticationAuditService).logLoginSuccess("vivekmanager");
    }

    @Test
    void shouldRejectInvalidCredentials() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(createUser("vivekadmin", "vivek.chavda@cresensolutions.com", "encoded-Password@123", "ADMIN")));
        when(passwordEncoder.matches("wrong-password", "encoded-Password@123")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class,
                () -> authService.login(new LoginRequest("vivekadmin", "d3JvbmctcGFzc3dvcmQ=")));
    }

    @Test
    void shouldRejectInactiveUserLogin() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.of(createUser(
                        "vivekadmin",
                        "vivek.chavda@cresensolutions.com",
                        "encoded-Password@123",
                        "ADMIN",
                        false
                )));

        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> authService.login(new LoginRequest("vivekadmin", "UGFzc3dvcmRAMTIz")));

        assertEquals("Your account is inactive. Please contact an administrator.", exception.getMessage());
    }

    @Test
    void shouldRejectUnknownUser() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class,
                () -> authService.login(new LoginRequest("unknown-user", "YWRtaW4xMjM=")));

        verify(authenticationAuditService).logLoginFailure("unknown-user");
    }

    @Test
    void shouldResetPassword() {
        UserAccount employee = createUser("vivekemployee", "vivekcchavda@cresen.com", "encoded-employee123", "EMPLOYEE");
        when(userRepository.findByEmailIdIgnoreCase("vivekcchavda@cresen.com"))
                .thenReturn(Optional.of(employee));
        when(userRepository.save(employee)).thenReturn(employee);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("encoded-NewPass@123");
        when(jwtService.generateToken(any(UserAccount.class)))
                .thenReturn("jwt-token");

        LoginResponse response = authService.resetPassword(
                new ResetPasswordWithOtpRequest("vivekcchavda@cresen.com", "123456", "TmV3UGFzc0AxMjM=")
        );

        assertEquals("vivekemployee", response.username());
        assertEquals("EMPLOYEE", response.role());
        assertEquals(true, response.active());
        assertEquals("jwt-token", response.token());
        assertEquals("Password reset successful", response.message());
        assertEquals("encoded-NewPass@123", employee.getPassword());
        verify(userRepository).save(employee);
        verify(passwordEncoder).encode("NewPass@123");
        verify(otpService).validateOtp(employee, "123456");
        verify(otpService).clearOtp(employee);
        verify(authenticationAuditService).logPasswordReset("vivekcchavda@cresen.com");
    }

    @Test
    void shouldRejectUnknownEmailDuringReset() {
        when(userRepository.findByEmailIdIgnoreCase("vivek@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> authService.resetPassword(new ResetPasswordWithOtpRequest("vivek@gmail.com", "123456", "NewPass@123")));
    }

    @Test
    void shouldRejectInactiveUserPasswordReset() {
        UserAccount employee = createUser(
                "vivekemployee",
                "vivekcchavda@cresen.com",
                "encoded-employee123",
                "EMPLOYEE",
                false
        );
        when(userRepository.findByEmailIdIgnoreCase("vivekcchavda@cresen.com"))
                .thenReturn(Optional.of(employee));

        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> authService.resetPassword(
                        new ResetPasswordWithOtpRequest("vivekcchavda@cresen.com", "123456", "TmV3UGFzc0AxMjM=")
                ));

        assertEquals("Your account is inactive. Please contact an administrator.", exception.getMessage());
    }

    @Test
    void shouldRequestPasswordResetOtp() {
        UserAccount employee = createUser("employee", "vivekcchavda@cresen.com", "encoded-employee123", "EMPLOYEE");
        when(userRepository.findByEmailIdIgnoreCase("vivekcchavda@cresen.com"))
                .thenReturn(Optional.of(employee));
        when(otpService.createOtp(employee)).thenReturn("123456");

        authService.requestPasswordResetOtp(new OtpRequest("vivekcchavda@cresen.com"));

        verify(emailService)
                .sendPasswordResetOtp("vivekcchavda@cresen.com", "employee", "123456");
    }

    @Test
    void shouldVerifyPasswordResetOtp() {
        UserAccount employee = createUser("employee", "vivekcchavda@cresen.com", "encoded-employee123", "EMPLOYEE");
        when(userRepository.findByEmailIdIgnoreCase("vivekcchavda@cresen.com"))
                .thenReturn(Optional.of(employee));

        OtpResponse response = authService.verifyPasswordResetOtp(
                new OtpValidationRequest(" VivekCChavda@Cresen.com ", "123456")
        );

        assertEquals("OTP verified successfully.", response.message());
        verify(otpService).validateOtp(employee, "123456");
    }

    @Test
    void shouldRejectUnknownEmailDuringOtpRequest() {
        when(userRepository.findByEmailIdIgnoreCase("vivek@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> authService.requestPasswordResetOtp(new OtpRequest("vivek@gmail.com")));

        verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectInactiveUserDuringOtpRequest() {
        UserAccount employee = createUser(
                "employee",
                "vivekcchavda@cresen.com",
                "encoded-employee123",
                "EMPLOYEE",
                false
        );
        when(userRepository.findByEmailIdIgnoreCase("vivekcchavda@cresen.com"))
                .thenReturn(Optional.of(employee));

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> authService.requestPasswordResetOtp(new OtpRequest("vivekcchavda@cresen.com"))
        );

        assertEquals("Your account is inactive. Please contact an administrator.", exception.getMessage());
        verify(otpService, never()).createOtp(employee);
        verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString(), anyString());
    }

    private Role toRole(String roleName) {
        return switch (roleName) {
            case "ADMIN", "Administrator" -> ADMIN_ROLE;
            case "MANAGER", "Manager" -> MANAGER_ROLE;
            case "EMPLOYEE", "Employee" -> EMPLOYEE_ROLE;
            default -> null;
        };
    }
}
