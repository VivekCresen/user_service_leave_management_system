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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailProperties mailProperties;

    public UserManagementServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailProperties mailProperties
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailProperties = mailProperties;
    }

    @Override
    public UserDashboardResponse getDashboard(String actorUsername) {
        UserAccount actor = loadActiveActor(actorUsername);
        List<UserAccount> visibleUsers = visibleUsersFor(actor);

        return new UserDashboardResponse(
                toManagedUserResponse(actor, actor, false),
                visibleUsers.stream()
                        .map(user -> toManagedUserResponse(user, actor, true))
                        .sorted(Comparator.comparing(ManagedUserResponse::username, String.CASE_INSENSITIVE_ORDER))
                        .toList(),
                assignableRolesFor(actor),
                canManageUsers(actor),
                visibleUsers.size(),
                visibleUsers.stream().filter(UserAccount::isActive).count(),
                visibleUsers.stream().filter(user -> !user.isActive()).count(),
                visibleUsers.stream().filter(user -> isRole(user, "ADMIN")).count(),
                visibleUsers.stream().filter(user -> isRole(user, "MANAGER")).count(),
                visibleUsers.stream().filter(user -> isRole(user, "EMPLOYEE")).count()
        );
    }

    @Override
    @Transactional
    public ManagedUserResponse createUser(CreateUserRequest request) {
        UserAccount actor = loadActiveActor(request.actorUsername());
        String companyId = requireTrimmedValue(request.companyId(), "Company id is required");
        String fullName = requireTrimmedValue(request.fullName(), "Full name is required");
        String username = requireTrimmedValue(request.username(), "Username is required");
        String email = requireTrimmedValue(request.email(), "Email is required").toLowerCase(Locale.ROOT);
        String password = requireTrimmedValue(request.password(), "Password is required");
        String roleName = requireTrimmedValue(request.role(), "Role is required").toUpperCase(Locale.ROOT);
        String gender = requireTrimmedValue(request.gender(), "Gender is required");

        ensureCanCreateRole(actor, roleName);

        validateUniqueUsername(null, username);
        validateUniqueEmail(null, email);
        validateOptionalPassword(password);

        Instant now = Instant.now();
        Role role = loadRole(roleName);

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
        emailService.sendNewUserCreatedEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getId(),
                savedUser.getCompanyId(),
                savedUser.getUsername(),
                password,
                savedUser.getRole(),
                mailProperties.forgotPasswordUrl()
        );

        return toManagedUserResponse(savedUser, actor, true);
    }

    @Override
    @Transactional
    public ManagedUserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserAccount actor = loadActiveActor(request.actorUsername());
        String companyId = requireTrimmedValue(request.companyId(), "Company id is required");
        String fullName = requireTrimmedValue(request.fullName(), "Full name is required");
        String username = requireTrimmedValue(request.username(), "Username is required");
        String email = requireTrimmedValue(request.email(), "Email is required").toLowerCase(Locale.ROOT);
        String roleName = requireTrimmedValue(request.role(), "Role is required").toUpperCase(Locale.ROOT);
        String gender = requireTrimmedValue(request.gender(), "Gender is required");
        String password = normalizeOptionalValue(request.password());
        UserAccount target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        ensureCanAssignRole(actor, target, roleName);
        validateOptionalPassword(password);
        validateUniqueUsername(target.getId(), username);
        validateUniqueEmail(target.getId(), email);

        target.setCompanyId(companyId);
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
        UserAccount target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        if (actor.getId().equals(target.getId())) {
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

    private List<UserAccount> visibleUsersFor(UserAccount actor) {
        List<UserAccount> allUsers = userRepository.findAllByOrderByUserNameAsc();
        if (isRole(actor, "ADMIN")) {
            return allUsers;
        }
        if (isRole(actor, "MANAGER")) {
            return allUsers.stream()
                    .filter(user -> isManagedEmployee(actor, user))
                    .toList();
        }
        return List.of(actor);
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

    private boolean canEdit(UserAccount actor, UserAccount user) {
        if (isRole(actor, "ADMIN")) {
            return !isRole(user, "ADMIN");
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

    private boolean canDelete(UserAccount actor, UserAccount user) {
        return canEdit(actor, user) && !Objects.equals(actor.getId(), user.getId());
    }

    private boolean isRole(UserAccount user, String roleName) {
        return user.getRole() != null
                && user.getRole().trim().toUpperCase(Locale.ROOT).equals(roleName);
    }
}
