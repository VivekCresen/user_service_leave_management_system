package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.ManagedUserResponse;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.validation.ValidationPatterns;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class UserManagementServiceImpl implements UserManagementService {
    private static final String COMPANY_ID_PREFIX = "CRESEN";
    private static final int COMPANY_ID_NUMBER_WIDTH = 3;
    private static final int MINIMUM_NEXT_COMPANY_ID = 4;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailProperties mailProperties;
    private final Executor dashboardTaskExecutor;

    public UserManagementServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailProperties mailProperties,
            @Qualifier("dashboardTaskExecutor") Executor dashboardTaskExecutor
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailProperties = mailProperties;
        this.dashboardTaskExecutor = dashboardTaskExecutor;
    }

    @Override
    public UserDashboardResponse getDashboard(String actorUsername) {
        UserAccount actor = loadActiveActor(actorUsername);
        ManagedUserResponse actorResponse = toManagedUserResponse(actor, actor, false);
        List<UserAccountView> visibleUsers = loadVisibleUsers(actor);
        CompletableFuture<List<ManagedUserResponse>> usersFuture = CompletableFuture.supplyAsync(
                () -> visibleUsers.stream()
                        .map(user -> toManagedUserResponse(user, actor))
                        .sorted(Comparator.comparing(ManagedUserResponse::username, String.CASE_INSENSITIVE_ORDER))
                        .toList(),
                dashboardTaskExecutor
        );
        CompletableFuture<UserDashboardMetrics> metricsFuture = CompletableFuture.supplyAsync(
                () -> summarizeUsers(visibleUsers),
                dashboardTaskExecutor
        );
        UserDashboardMetrics metrics = metricsFuture.join();

        return new UserDashboardResponse(
                actorResponse,
                usersFuture.join(),
                assignableRolesFor(actor),
                canManageUsers(actor),
                metrics.totalUsers(),
                metrics.activeUsers(),
                metrics.inactiveUsers(),
                metrics.adminCount(),
                metrics.managerCount(),
                metrics.employeeCount()
        );
    }

    @Override
    @Transactional
    public ManagedUserResponse createUser(CreateUserRequest request) {
        UserAccount actor = loadActiveActor(request.actorUsername());
        String fullName = requireTrimmedValue(request.fullName(), "Full name is required");
        String username = requireTrimmedValue(request.username(), "Username is required");
        String email = requireTrimmedValue(request.email(), "Email is required").toLowerCase(Locale.ROOT);
        String base64Password = requireTrimmedValue(request.password(), "Password is required");
        String password = new String(java.util.Base64.getDecoder().decode(base64Password), java.nio.charset.StandardCharsets.UTF_8);
        String roleName = requireTrimmedValue(request.role(), "Role is required").toUpperCase(Locale.ROOT);
        String gender = requireTrimmedValue(request.gender(), "Gender is required");

        ensureCanCreateRole(actor, roleName);

        validateUniqueUsername(null, username);
        validateUniqueEmail(null, email);
        validateOptionalPassword(password);

        Instant now = Instant.now();
        Role role = loadRole(roleName);
        String companyId = generateNextCompanyId();

        UserAccount user = new UserAccount(
                fullName,
                email,
                username,
                passwordEncoder.encode(password),
                role
        );
        user.setCompanyId(companyId);
        user.setActive(Boolean.TRUE.equals(request.active()));
        user.setGender(gender);
        user.setCreateDate(now);
        user.setUpdateDate(now);
        user.setCreatedBy(actor.getUsername());
        user.setUpdatedBy(actor.getUsername());

        UserAccount savedUser = userRepository.save(user);
        runAfterCommit(() -> emailService.sendNewUserCreatedEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getId(),
                savedUser.getCompanyId(),
                savedUser.getUsername(),
                savedUser.getRole(),
                mailProperties.forgotPasswordUrl()
        ));

        return toManagedUserResponse(savedUser, actor, true);
    }

    @Override
    @Transactional
    public ManagedUserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserAccount actor = loadActiveActor(request.actorUsername());
        String fullName = requireTrimmedValue(request.fullName(), "Full name is required");
        String username = requireTrimmedValue(request.username(), "Username is required");
        String email = requireTrimmedValue(request.email(), "Email is required").toLowerCase(Locale.ROOT);
        String roleName = requireTrimmedValue(request.role(), "Role is required").toUpperCase(Locale.ROOT);
        String gender = requireTrimmedValue(request.gender(), "Gender is required");
        String rawPassword = normalizeOptionalValue(request.password());
        String password = rawPassword != null ? new String(java.util.Base64.getDecoder().decode(rawPassword), java.nio.charset.StandardCharsets.UTF_8) : null;
        UserAccount target = userRepository.findDetailedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        ensureCanAssignRole(actor, target, roleName);
        validateOptionalPassword(password);
        validateUniqueUsername(target.getId(), username);
        validateUniqueEmail(target.getId(), email);

        target.setFullName(fullName);
        target.setUsername(username);
        target.setEmail(email);
        target.setGender(gender);
        target.setActive(Boolean.TRUE.equals(request.active()));
        target.assignRole(loadRole(roleName));
        target.setUpdateDate(Instant.now());
        target.setUpdatedBy(actor.getUsername());

        if (password != null && !password.isBlank()) {
            target.setPassword(passwordEncoder.encode(password));
        }

        return toManagedUserResponse(userRepository.save(target), actor, true);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, String actorUsername) {
        UserAccount actor = loadActiveActor(actorUsername);
        UserAccount target = userRepository.findDetailedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        if (isSameUser(actor, target)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        userRepository.delete(target);
    }

    private UserAccount loadActiveActor(String actorUsername) {
        UserAccount actor = userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(actorUsername, actorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Actor account not found."));

        if (!actor.isActive()) {
            throw new AccessDeniedException("Inactive users cannot perform this action.");
        }

        return actor;
    }

    private List<UserAccountView> loadVisibleUsers(UserAccount actor) {
        try (Stream<UserAccount> users = userRepository.streamAllByOrderByUserNameAsc()) {
            return users.filter(user -> canViewUser(actor, user))
                    .map(this::toUserAccountView)
                    .toList();
        }
    }

    private List<String> assignableRolesFor(UserAccount actor) {
        if (isRole(actor, "ADMIN")) {
            return List.of("MANAGER", "EMPLOYEE");
        }
        if (isRole(actor, "MANAGER")) {
            return List.of("EMPLOYEE");
        }
        return List.of();
    }

    private boolean canManageUsers(UserAccount actor) {
        return isRole(actor, "ADMIN") || isRole(actor, "MANAGER");
    }

    private UserDashboardMetrics summarizeUsers(List<UserAccountView> users) {
        long activeUsers = users.stream()
                .filter(UserAccountView::active)
                .count();
        Map<String, Long> roleCounts = users.stream()
                .map(UserAccountView::role)
                .map(this::normalizeRoleName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return new UserDashboardMetrics(
                users.size(),
                activeUsers,
                users.size() - activeUsers,
                roleCounts.getOrDefault("ADMIN", 0L),
                roleCounts.getOrDefault("MANAGER", 0L),
                roleCounts.getOrDefault("EMPLOYEE", 0L)
        );
    }

    private String requireTrimmedValue(String value, String message) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptionalValue(String value) {
        return value == null ? null : value.trim();
    }

    private void ensureCanCreateRole(UserAccount actor, String requestedRole) {
        if (!canManageUsers(actor)) {
            throw new AccessDeniedException("You do not have permission to create users.");
        }
        if (isRole(actor, "ADMIN") && !isSupportedAdminRole(requestedRole)) {
            throw new AccessDeniedException("Admins can create managers and employees only.");
        }
        if (isRole(actor, "MANAGER") && !"EMPLOYEE".equalsIgnoreCase(requestedRole)) {
            throw new AccessDeniedException("Managers can create employees only.");
        }
    }

    private void ensureCanAssignRole(UserAccount actor, UserAccount target, String requestedRole) {
        if (isRole(actor, "ADMIN")
                && !isRole(target, "ADMIN")
                && isSupportedAdminRole(requestedRole)) {
            return;
        }
        if (isRole(actor, "MANAGER")
                && isManagedEmployee(actor, target)
                && "EMPLOYEE".equalsIgnoreCase(requestedRole)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to change this user.");
    }

    private void ensureManageableTarget(UserAccount actor, UserAccount target) {
        if (isRole(actor, "ADMIN") && !isRole(target, "ADMIN")) {
            return;
        }
        if (isRole(actor, "MANAGER") && isManagedEmployee(actor, target)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to manage this user.");
    }

    private void validateUniqueUsername(Long currentUserId, String username) {
        userRepository.findByUserNameIgnoreCase(username)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Username is already in use.");
                });
    }

    private String generateNextCompanyId() {
        List<String> existingCompanyIds = userRepository.findAllCompanyIds().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        int nextNumber = Math.max(
                MINIMUM_NEXT_COMPANY_ID,
                Math.max((int) userRepository.count() + 1, highestExistingCompanyId(existingCompanyIds) + 1)
        );

        String candidate = formatCompanyId(nextNumber);
        while (containsCompanyId(existingCompanyIds, candidate)) {
            nextNumber++;
            candidate = formatCompanyId(nextNumber);
        }

        return candidate;
    }

    private int highestExistingCompanyId(List<String> companyIds) {
        return companyIds.stream()
                .mapToInt(this::extractCompanyIdNumber)
                .max()
                .orElse(MINIMUM_NEXT_COMPANY_ID - 1);
    }

    private int extractCompanyIdNumber(String companyId) {
        if (!companyId.regionMatches(true, 0, COMPANY_ID_PREFIX, 0, COMPANY_ID_PREFIX.length())) {
            return -1;
        }

        String suffix = companyId.substring(COMPANY_ID_PREFIX.length()).trim();
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
            return -1;
        }

        return Integer.parseInt(suffix);
    }

    private boolean containsCompanyId(List<String> companyIds, String candidate) {
        return companyIds.stream().anyMatch(existing -> existing.equalsIgnoreCase(candidate));
    }

    private String formatCompanyId(int number) {
        return COMPANY_ID_PREFIX + String.format(Locale.ROOT, "%0" + COMPANY_ID_NUMBER_WIDTH + "d", number);
    }

    private void validateUniqueEmail(Long currentUserId, String email) {
        userRepository.findByEmailIdIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Email is already in use.");
                });
    }

    private void validateOptionalPassword(String password) {
        if (password == null || password.isBlank()) {
            return;
        }

        if (password.length() < ValidationPatterns.PASSWORD_MIN_LENGTH
                || password.length() > ValidationPatterns.PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be between 8 and 255 characters");
        }

        if (!password.matches(ValidationPatterns.STRICT_PASSWORD_REGEX)) {
            throw new IllegalArgumentException(ValidationPatterns.STRICT_PASSWORD_MESSAGE);
        }
    }

    private Role loadRole(String uniqueName) {
        return roleRepository.findByUniqueNameIgnoreCase(uniqueName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + uniqueName));
    }

    private ManagedUserResponse toManagedUserResponse(UserAccount user, UserAccount actor, boolean includePermissions) {
        return new ManagedUserResponse(
                user.getId(),
                user.getCompanyId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getGender(),
                user.getCreatedBy(),
                user.getUpdatedBy(),
                user.getCreateDate(),
                user.getUpdateDate(),
                user.getLastLogin(),
                includePermissions && canEdit(actor, user),
                includePermissions && canDelete(actor, user)
        );
    }

    private ManagedUserResponse toManagedUserResponse(UserAccountView user, UserAccount actor) {
        return new ManagedUserResponse(
                user.id(),
                user.companyId(),
                user.username(),
                user.fullName(),
                user.email(),
                user.role(),
                user.active(),
                user.gender(),
                user.createdBy(),
                user.updatedBy(),
                user.createDate(),
                user.updateDate(),
                user.lastLogin(),
                canEdit(actor, user),
                canDelete(actor, user)
        );
    }

    private UserAccountView toUserAccountView(UserAccount user) {
        return new UserAccountView(
                user.getId(),
                user.getCompanyId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getGender(),
                user.getCreatedBy(),
                user.getUpdatedBy(),
                user.getCreateDate(),
                user.getUpdateDate(),
                user.getLastLogin()
        );
    }

    private boolean canEdit(UserAccount actor, UserAccount user) {
        if (isRole(actor, "ADMIN")) {
            return !isRole(user, "ADMIN");
        }
        if (isRole(actor, "MANAGER")) {
            return isManagedEmployee(actor, user);
        }
        return false;
    }

    private boolean canEdit(UserAccount actor, UserAccountView user) {
        if (isRole(actor, "ADMIN")) {
            return !isRole(user.role(), "ADMIN");
        }
        if (isRole(actor, "MANAGER")) {
            return isManagedEmployee(actor, user);
        }
        return false;
    }

    private boolean isSupportedAdminRole(String roleName) {
        return "MANAGER".equalsIgnoreCase(roleName) || "EMPLOYEE".equalsIgnoreCase(roleName);
    }

    private boolean isManagedEmployee(UserAccount actor, UserAccount user) {
        if (!isRole(user, "EMPLOYEE")) {
            return false;
        }

        String createdBy = user.getCreatedBy();
        if (createdBy == null || createdBy.isBlank()) {
            return false;
        }

        return createdBy.trim().equalsIgnoreCase(actor.getUsername());
    }

    private boolean isManagedEmployee(UserAccount actor, UserAccountView user) {
        if (!isRole(user.role(), "EMPLOYEE")) {
            return false;
        }

        String createdBy = user.createdBy();
        if (createdBy == null || createdBy.isBlank()) {
            return false;
        }

        return createdBy.trim().equalsIgnoreCase(actor.getUsername());
    }

    private boolean canDelete(UserAccount actor, UserAccount user) {
        return canEdit(actor, user) && !Objects.equals(actor.getId(), user.getId());
    }

    private boolean canDelete(UserAccount actor, UserAccountView user) {
        return canEdit(actor, user) && !isSameUser(actor, user);
    }

    private boolean isRole(UserAccount user, String roleName) {
        return isRole(user.getRole(), roleName);
    }

    private boolean isRole(String currentRole, String roleName) {
        return normalizeRoleName(currentRole).equals(roleName);
    }

    private String normalizeRoleName(String roleName) {
        return roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
    }

    private boolean canViewUser(UserAccount actor, UserAccount user) {
        if (isRole(actor, "ADMIN")) {
            return true;
        }
        if (isRole(actor, "MANAGER")) {
            return isManagedEmployee(actor, user);
        }
        return isSameUser(actor, user);
    }

    private boolean isSameUser(UserAccount actor, UserAccount user) {
        if (actor.getId() != null && user.getId() != null) {
            return actor.getId().equals(user.getId());
        }
        return actor.getUsername() != null
                && user.getUsername() != null
                && actor.getUsername().equalsIgnoreCase(user.getUsername());
    }

    private boolean isSameUser(UserAccount actor, UserAccountView user) {
        if (actor.getId() != null && user.id() != null) {
            return actor.getId().equals(user.id());
        }
        return actor.getUsername() != null
                && user.username() != null
                && actor.getUsername().equalsIgnoreCase(user.username());
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

    private record UserDashboardMetrics(
            long totalUsers,
            long activeUsers,
            long inactiveUsers,
            long adminCount,
            long managerCount,
            long employeeCount
    ) {
    }

    private record UserAccountView(
            Long id,
            String companyId,
            String username,
            String fullName,
            String email,
            String role,
            boolean active,
            String gender,
            String createdBy,
            String updatedBy,
            Instant createDate,
            Instant updateDate,
            Instant lastLogin
    ) {
    }
}
