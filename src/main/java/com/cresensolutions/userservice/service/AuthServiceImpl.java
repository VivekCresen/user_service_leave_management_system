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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        UserAccount savedUser = userRepository.save(user);
        runAfterCommit(() -> authenticationAuditService.logLoginSuccess(user.getUsername()));
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
        runAfterCommit(() -> emailService.sendPasswordResetOtp(user.getEmail(), user.getFullName(), otp));
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
        UserAccount savedUser = userRepository.save(user);
        otpService.clearOtp(user);
        runAfterCommit(() -> authenticationAuditService.logPasswordReset(email));
        return toLoginResponse(savedUser, "Password reset successful");
    }

    @Override
    public List<RoleSummaryResponse> fetchRoleSummary() {
        List<Role> roles = roleRepository.findAllByOrderByIdAsc();
        Map<String, String> canonicalRoleByAlias = buildCanonicalRoleMap(roles);
        Map<String, NavigableSet<String>> usernamesByRole;

        try (Stream<UserAccount> users = userRepository.streamAllByOrderByUserNameAsc()) {
            usernamesByRole = users
                    .map(user -> toRoleAssignment(user, canonicalRoleByAlias))
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            RoleAssignment::roleName,
                            LinkedHashMap::new,
                            Collectors.mapping(
                                    RoleAssignment::username,
                                    Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                            )
                    ));
        }

        return roles.stream()
                .map(role -> toRoleSummary(role, usernamesByRole))
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

    private RoleSummaryResponse toRoleSummary(Role role, Map<String, NavigableSet<String>> usernamesByRole) {
        NavigableSet<String> usernames = usernamesByRole.getOrDefault(
                normalizeRoleName(role.getSummaryName()),
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER)
        );

        return new RoleSummaryResponse(
                role.getId(),
                role.getSummaryName(),
                usernames.size(),
                new ArrayList<>(usernames)
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

    private Map<String, String> buildCanonicalRoleMap(List<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.of(role.getRoleName(), role.getUniqueName(), role.getSummaryName())
                        .filter(Objects::nonNull)
                        .map(alias -> Map.entry(normalizeRoleName(alias), normalizeRoleName(role.getSummaryName()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, ignored) -> existing, LinkedHashMap::new));
    }

    private RoleAssignment toRoleAssignment(UserAccount user, Map<String, String> canonicalRoleByAlias) {
        String username = user.getUsername();
        String normalizedRole = normalizeRoleName(user.getRole());
        if (username == null || username.isBlank() || normalizedRole.isBlank()) {
            return null;
        }

        return new RoleAssignment(
                canonicalRoleByAlias.getOrDefault(normalizedRole, normalizedRole),
                username
        );
    }

    private String normalizeRoleName(String roleName) {
        return roleName == null ? "" : roleName.trim().toUpperCase();
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record RoleAssignment(String roleName, String username) {
    }
}
