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
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationAuditService authenticationAuditService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            RoleRepository roleRepository,
            UserRepository userRepository,
            JwtService jwtService,
            AuthenticationAuditService authenticationAuditService,
            OtpService otpService,
            EmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationAuditService = authenticationAuditService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String loginValue = normalize(request.username());
        UserAccount user = userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(loginValue, loginValue)
                .orElseThrow(() -> failedLogin(loginValue));

        ensureActiveUser(user);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw failedLogin(loginValue);
        }

        user.setLastLogin(Instant.now());
        UserAccount savedUser = userRepository.saveAndFlush(user);
        authenticationAuditService.logLoginSuccess(user.getUsername());
        return toLoginResponse(savedUser, "Login successful");
    }

    @Override
    @Transactional
    public OtpResponse requestPasswordResetOtp(OtpRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount user = userRepository.findByEmailIdIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email."));

        ensureActiveUser(user);

        String otp = otpService.createOtp(user);
        emailService.sendPasswordResetOtp(user.getEmail(), user.getFullName(), otp);
        return new OtpResponse("OTP sent to your email address.");
    }

    @Override
    public OtpResponse verifyPasswordResetOtp(OtpValidationRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount user = userRepository.findByEmailIdIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email."));

        ensureActiveUser(user);
        otpService.validateOtp(user, request.otp());
        return new OtpResponse("OTP verified successfully.");
    }

    @Override
    @Transactional
    public LoginResponse resetPassword(ResetPasswordWithOtpRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount user = userRepository.findByEmailIdIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email."));

        ensureActiveUser(user);

        otpService.validateOtp(user, request.otp());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        UserAccount savedUser = userRepository.saveAndFlush(user);
        otpService.clearOtp(user);
        authenticationAuditService.logPasswordReset(email);
        return toLoginResponse(savedUser, "Password reset successful");
    }

    @Override
    public List<RoleSummaryResponse> fetchRoleSummary() {
        List<UserAccount> users = userRepository.findAllByOrderByUserNameAsc();

        return roleRepository.findAllByOrderByIdAsc().stream()
                .map(role -> toRoleSummary(role, users))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private AuthenticationFailedException failedLogin(String loginValue) {
        authenticationAuditService.logLoginFailure(loginValue);
        return new AuthenticationFailedException("Invalid username or password");
    }

    private RoleSummaryResponse toRoleSummary(Role role, List<UserAccount> users) {
        Set<String> usernames = users.stream()
                .filter(user -> user.getRoleId() != null
                        ? role.getId().equals(user.getRoleId())
                        : role.matchesUserRole(user.getStoredRole()))
                .map(UserAccount::getUsername)
                .filter(Objects::nonNull)
                .collect(TreeSet::new, Set::add, Set::addAll);

        return new RoleSummaryResponse(
                role.getId(),
                role.getSummaryName(),
                usernames.size(),
                usernames.stream()
                        .sorted(Comparator.naturalOrder())
                        .toList()
        );
    }

    private LoginResponse toLoginResponse(UserAccount user, String message) {
        return new LoginResponse(
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                jwtService.generateToken(user),
                message
        );
    }

    private void ensureActiveUser(UserAccount user) {
        if (!user.isActive()) {
            throw new AuthenticationFailedException("Your account is inactive. Please contact an administrator.");
        }
    }
}
