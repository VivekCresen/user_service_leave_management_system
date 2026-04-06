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
import com.cresensolutions.userservice.validation.ValidationPatterns;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
        String rawPassword = decodeBase64Password(request.password());

        ensureActiveUser(user);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw failedLogin(loginValue);
        }

        user.setLastLogin(Instant.now());
        runAfterCommit(() -> authenticationAuditService.logLoginSuccess(user.getUsername()));
        return toLoginResponse(user, "Login successful");
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
        UserAccount user = loadActiveUserByEmail(email);
        String decodedPassword = decodeBase64Password(request.newPassword());
        validatePassword(decodedPassword);

        otpService.validateOtp(user, request.otp());
        user.setPassword(passwordEncoder.encode(decodedPassword));
        otpService.clearOtp(user);
        runAfterCommit(() -> authenticationAuditService.logPasswordReset(email));
        return toLoginResponse(user, "Password reset successful");
    }

    @Override
    public List<RoleSummaryResponse> fetchRoleSummary() {
        List<Role> roles = roleRepository.findAllByOrderByIdAsc();
        Map<String, String> canonicalRoleByAlias = buildCanonicalRoleMap(roles);
        Map<String, NavigableSet<String>> usernamesByRole;
        usernamesByRole = userRepository.findRoleAssignments().stream()
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

    private UserAccount loadActiveUserByEmail(String email) {
        UserAccount user = userRepository.findByEmailIdIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email."));
        ensureActiveUser(user);
        return user;
    }

    private Map<String, String> buildCanonicalRoleMap(List<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.of(role.getRoleName(), role.getUniqueName(), role.getSummaryName())
                        .filter(Objects::nonNull)
                        .map(alias -> Map.entry(normalizeRoleName(alias), normalizeRoleName(role.getSummaryName()))))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue(), (existing, ignored) -> existing, LinkedHashMap::new));
    }

    private RoleAssignment toRoleAssignment(UserRepository.RoleAssignmentView user, Map<String, String> canonicalRoleByAlias) {
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

    private String decodeBase64Password(String encodedPassword) {
        try {
            return new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Password must be valid Base64.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (password.length() < ValidationPatterns.PASSWORD_MIN_LENGTH
                || password.length() > ValidationPatterns.PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be between 8 and 255 characters");
        }

        if (!password.matches(ValidationPatterns.STRICT_PASSWORD_REGEX)) {
            throw new IllegalArgumentException(ValidationPatterns.STRICT_PASSWORD_MESSAGE);
        }
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
