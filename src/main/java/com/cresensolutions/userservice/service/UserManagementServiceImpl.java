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

@Service
@Transactional(readOnly = true)
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
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
        ensureCanCreateRole(actor, request.role());

        validateUniqueUsername(null, request.username());
        validateUniqueEmail(null, request.email());

        Instant now = Instant.now();
        Role role = loadRole(request.role());

        UserAccount user = new UserAccount(
                request.fullName(),
                request.email(),
                request.username(),
                passwordEncoder.encode(request.password()),
                role
        );
        user.setCompanyId(request.companyId());
        user.setActive(Boolean.TRUE.equals(request.active()));
        user.setGender(request.gender());
        user.setCreateDate(now);
        user.setUpdateDate(now);
        user.setCreatedBy(actor.getUsername());
        user.setUpdatedBy(actor.getUsername());

        return toManagedUserResponse(userRepository.save(user), actor, true);
    }

    @Override
    @Transactional
    public ManagedUserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserAccount actor = loadActiveActor(request.actorUsername());
        UserAccount target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        ensureCanAssignRole(actor, target, request.role());
        validateOptionalPassword(request.password());
        validateUniqueUsername(target.getId(), request.username());
        validateUniqueEmail(target.getId(), request.email());

        target.setCompanyId(request.companyId());
        target.setFullName(request.fullName());
        target.setUsername(request.username());
        target.setEmail(request.email());
        target.setGender(request.gender());
        target.setActive(Boolean.TRUE.equals(request.active()));
        target.assignRole(loadRole(request.role()));
        target.setUpdateDate(Instant.now());
        target.setUpdatedBy(actor.getUsername());

        if (request.password() != null && !request.password().isBlank()) {
            target.setPassword(passwordEncoder.encode(request.password()));
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
                    .filter(user -> isRole(user, "EMPLOYEE"))
                    .toList();
        }
        return List.of(actor);
    }

    private List<String> assignableRolesFor(UserAccount actor) {
        if (isRole(actor, "ADMIN")) {
            return List.of("ADMIN", "MANAGER", "EMPLOYEE");
        }
        if (isRole(actor, "MANAGER")) {
            return List.of("EMPLOYEE");
        }
        return List.of();
    }

    private boolean canManageUsers(UserAccount actor) {
        return isRole(actor, "ADMIN") || isRole(actor, "MANAGER");
    }

    private void ensureCanCreateRole(UserAccount actor, String requestedRole) {
        if (!canManageUsers(actor)) {
            throw new AccessDeniedException("You do not have permission to create users.");
        }
        if (isRole(actor, "MANAGER") && !"EMPLOYEE".equalsIgnoreCase(requestedRole)) {
            throw new AccessDeniedException("Managers can create employees only.");
        }
    }

    private void ensureCanAssignRole(UserAccount actor, UserAccount target, String requestedRole) {
        if (isRole(actor, "ADMIN")) {
            return;
        }
        if (isRole(actor, "MANAGER") && isRole(target, "EMPLOYEE") && "EMPLOYEE".equalsIgnoreCase(requestedRole)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to change this user.");
    }

    private void ensureManageableTarget(UserAccount actor, UserAccount target) {
        if (isRole(actor, "ADMIN")) {
            return;
        }
        if (isRole(actor, "MANAGER") && isRole(target, "EMPLOYEE")) {
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
            return true;
        }
        return isRole(actor, "MANAGER") && isRole(user, "EMPLOYEE");
    }

    private boolean canDelete(UserAccount actor, UserAccount user) {
        return canEdit(actor, user) && !actor.getId().equals(user.getId());
    }

    private boolean isRole(UserAccount user, String roleName) {
        return user.getRole() != null
                && user.getRole().trim().toUpperCase(Locale.ROOT).equals(roleName);
    }
}
