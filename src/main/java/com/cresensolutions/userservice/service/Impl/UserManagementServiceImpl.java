package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.ManagedUserResponse;
import com.cresensolutions.userservice.dto.UpdateProfileRequest;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.model.Country;
import com.cresensolutions.userservice.model.PhoneCode;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.CountryRepository;
import com.cresensolutions.userservice.repository.PhoneCodeRepository;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.common.StringUtils;
import com.cresensolutions.userservice.common.TransactionUtils;
import com.cresensolutions.userservice.common.UserConstants;
import com.cresensolutions.userservice.service.EmailService;
import com.cresensolutions.userservice.service.MailProperties;
import com.cresensolutions.userservice.service.UserManagementService;
import com.cresensolutions.userservice.validation.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CountryRepository countryRepository;
    private final PhoneCodeRepository phoneCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailProperties mailProperties;
    private final Executor dashboardTaskExecutor;

    public UserManagementServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            CountryRepository countryRepository,
            PhoneCodeRepository phoneCodeRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailProperties mailProperties,
            @Qualifier("dashboardTaskExecutor") Executor dashboardTaskExecutor
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.countryRepository = countryRepository;
        this.phoneCodeRepository = phoneCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailProperties = mailProperties;
        this.dashboardTaskExecutor = dashboardTaskExecutor;
    }

    @Override
    public UserDashboardResponse getDashboard(String actorUsername) {
        UserAccount actor = loadActiveActor(actorUsername);
        log.debug("Loading dashboard for actor {}", actor.getUsername());
        ManagedUserResponse actorResponse = toManagedUserResponse(actor, actor, false);
        List<UserAccountView> visibleUsers = loadVisibleUsers(actor);
        List<ManagedUserResponse> managedUsers = visibleUsers.stream()
                .map(user -> toManagedUserResponse(user, actor))
                .sorted(Comparator.comparing(ManagedUserResponse::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
        UserDashboardMetrics metrics = summarizeUsers(visibleUsers);

        return new UserDashboardResponse(
                actorResponse,
                managedUsers,
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
        String password = decodeRequiredBase64Password(request.password());
        String roleName = requireTrimmedValue(request.role(), "Role is required").toUpperCase(Locale.ROOT);
        String managerUsername = normalizeOptionalValue(request.managerUsername());
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
        user.setCreatedBy(resolveOwnerUsername(actor, roleName, managerUsername));
        user.setUpdatedBy(actor.getUsername());

        applyContactInfo(user, request.countryId(), request.phoneCodeId(), request.phoneNumber());

        UserAccount savedUser = userRepository.save(user);
        log.info("User {} created by {} with role {}", savedUser.getUsername(), actor.getUsername(), savedUser.getRole());
        TransactionUtils.runAfterCommit(() -> emailService.sendNewUserCreatedEmail(
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
        String password = decodeOptionalBase64Password(request.password());
        UserAccount target = userRepository.findDetailedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        ensureCanAssignRole(actor, target, roleName);
        validateOptionalPassword(password);
        validateUniqueUsername(target.getId(), username);
        validateUniqueEmail(target.getId(), email);
        String previousRole = target.getRole();
        boolean roleChangedByAdmin = isRole(actor, UserConstants.ROLE_ADMIN) && !isRole(previousRole, roleName);

        target.setFullName(fullName);
        target.setUsername(username);
        target.setEmail(email);
        target.setGender(gender);
        target.setActive(Boolean.TRUE.equals(request.active()));
        target.assignRole(loadRole(roleName));
        target.setUpdateDate(Instant.now());
        target.setUpdatedBy(actor.getUsername());

        applyContactInfo(target, request.countryId(), request.phoneCodeId(), request.phoneNumber());

        if (password != null && !password.isBlank()) {
            target.setPassword(passwordEncoder.encode(password));
        }

        UserAccount updatedUser = target;
        log.info("User {} updated by {}", updatedUser.getUsername(), actor.getUsername());
        if (roleChangedByAdmin) {
            TransactionUtils.runAfterCommit(() -> emailService.sendUserRoleChangedEmail(
                    updatedUser.getEmail(),
                    updatedUser.getFullName(),
                    updatedUser.getUsername(),
                    previousRole,
                    updatedUser.getRole(),
                    actor.getUsername(),
                    actor.getRole(),
                    mailProperties.loginUrl()
            ));
        }
        return toManagedUserResponse(updatedUser, actor, true);
    }

    @Override
    @Transactional
    public ManagedUserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        UserAccount actor = loadActiveActor(request.actorUsername());
        UserAccount target = userRepository.findDetailedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!isSameUser(actor, toUserAccountView(target))) {
            throw new AccessDeniedException("You can only update your own profile.");
        }

        target.setFullName(requireTrimmedValue(request.fullName(), "Full name is required"));
        target.setGender(requireTrimmedValue(request.gender(), "Gender is required"));
        target.setUpdateDate(Instant.now());
        target.setUpdatedBy(actor.getUsername());

        log.info("Profile updated for user {} by themselves", target.getUsername());
        return toManagedUserResponse(target, actor, false);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, String actorUsername) {
        UserAccount actor = loadActiveActor(actorUsername);
        UserAccount target = userRepository.findDetailedById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ensureManageableTarget(actor, target);
        if (isSameUser(actor, toUserAccountView(target))) {
            log.warn("User {} attempted to delete their own account", actor.getUsername());
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        String deletedUserEmail = target.getEmail();
        String deletedUserFullName = target.getFullName();
        String deletedUserUsername = target.getUsername();
        String deletedUserRole = target.getRole();
        userRepository.delete(target);
        log.info("User {} deleted by {}", target.getUsername(), actor.getUsername());
        TransactionUtils.runAfterCommit(() -> emailService.sendUserDeletedEmail(
                deletedUserEmail,
                deletedUserFullName,
                deletedUserUsername,
                deletedUserRole,
                actor.getUsername(),
                actor.getRole()
        ));
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
        if (isRole(actor, UserConstants.ROLE_ADMIN)) {
            return userRepository.findAllDetailedByOrderByUserNameAsc().stream()
                    .map(this::toUserAccountView)
                    .toList();
        }

        if (isRole(actor, UserConstants.ROLE_MANAGER)) {
            return userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc(
                            actor.getUsername(),
                            UserConstants.ROLE_EMPLOYEE
                    ).stream()
                    .map(this::toUserAccountView)
                    .toList();
        }

        return userRepository.findAllByIdOrderByUserNameAsc(actor.getId()).stream()
                .map(this::toUserAccountView)
                .toList();
    }

    private List<String> assignableRolesFor(UserAccount actor) {
        if (isRole(actor, UserConstants.ROLE_ADMIN)) {
            return List.of(UserConstants.ROLE_ADMIN, UserConstants.ROLE_MANAGER, UserConstants.ROLE_EMPLOYEE);
        }
        if (isRole(actor, UserConstants.ROLE_MANAGER)) {
            return List.of(UserConstants.ROLE_EMPLOYEE);
        }
        return List.of();
    }

    private boolean canManageUsers(UserAccount actor) {
        return isRole(actor, UserConstants.ROLE_ADMIN) || isRole(actor, UserConstants.ROLE_MANAGER);
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
                roleCounts.getOrDefault(UserConstants.ROLE_ADMIN, 0L),
                roleCounts.getOrDefault(UserConstants.ROLE_MANAGER, 0L),
                roleCounts.getOrDefault(UserConstants.ROLE_EMPLOYEE, 0L)
        );
    }

    private String requireTrimmedValue(String value, String message) {
        return StringUtils.requireNonBlank(value, message);
    }

    private String normalizeOptionalValue(String value) {
        return StringUtils.normalizeOptional(value);
    }

    private void ensureCanCreateRole(UserAccount actor, String requestedRole) {
        if (!canManageUsers(actor)) {
            throw new AccessDeniedException("You do not have permission to create users.");
        }
        if (isRole(actor, UserConstants.ROLE_ADMIN) && !isSupportedAdminRole(requestedRole)) {
            throw new AccessDeniedException("Admins can create managers and employees only.");
        }
        if (isRole(actor, UserConstants.ROLE_MANAGER) && !UserConstants.ROLE_EMPLOYEE.equalsIgnoreCase(requestedRole)) {
            throw new AccessDeniedException("Managers can create employees only.");
        }
    }

    private String resolveOwnerUsername(UserAccount actor, String roleName, String managerUsername) {
        if (isRole(actor, UserConstants.ROLE_MANAGER)) {
            return actor.getUsername();
        }

        if (isRole(actor, UserConstants.ROLE_ADMIN) && UserConstants.ROLE_EMPLOYEE.equalsIgnoreCase(roleName)) {
            String normalizedManagerUsername = requireTrimmedValue(managerUsername, "Manager is required for employee creation.");
            UserAccount manager = userRepository.findByUserNameIgnoreCase(normalizedManagerUsername)
                    .orElseThrow(() -> new IllegalArgumentException("Selected manager was not found."));

            if (!manager.isActive()) {
                throw new IllegalArgumentException("Selected manager must be active.");
            }

            if (!isRole(manager, UserConstants.ROLE_MANAGER)) {
                throw new IllegalArgumentException("Selected user must have the manager role.");
            }

            return manager.getUsername();
        }

        return actor.getUsername();
    }

    private void ensureCanAssignRole(UserAccount actor, UserAccount target, String requestedRole) {
        if (isRole(actor, UserConstants.ROLE_ADMIN)
                && isSupportedAdminRole(requestedRole)) {
            return;
        }
        if (isRole(actor, UserConstants.ROLE_MANAGER)
                && isManagedEmployee(actor, toUserAccountView(target))
                && UserConstants.ROLE_EMPLOYEE.equalsIgnoreCase(requestedRole)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to change this user.");
    }

    private void ensureManageableTarget(UserAccount actor, UserAccount target) {
        if (isRole(actor, UserConstants.ROLE_ADMIN)) {
            return;
        }
        if (isRole(actor, UserConstants.ROLE_MANAGER) && isManagedEmployee(actor, toUserAccountView(target))) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to manage this user.");
    }

    private void validateUniqueUsername(Long currentUserId, String username) {
        boolean exists = currentUserId == null
                ? userRepository.existsByUserNameIgnoreCase(username)
                : userRepository.existsByUserNameIgnoreCaseAndIdNot(username, currentUserId);

        if (exists) {
            throw new IllegalArgumentException("Username is already in use.");
        }
    }

    private String generateNextCompanyId() {
        int nextNumber = Math.max(
                UserConstants.MINIMUM_NEXT_COMPANY_ID,
                userRepository.findHighestCompanyIdNumber(
                        UserConstants.COMPANY_ID_PREFIX,
                        UserConstants.COMPANY_ID_PREFIX.length() + 1
                ) + 1
        );

        String candidate = formatCompanyId(nextNumber);
        while (userRepository.existsByCompanyIdIgnoreCase(candidate)) {
            nextNumber++;
            candidate = formatCompanyId(nextNumber);
        }

        return candidate;
    }

    private String formatCompanyId(int number) {
        return UserConstants.COMPANY_ID_PREFIX
                + String.format(Locale.ROOT, "%0" + UserConstants.COMPANY_ID_NUMBER_WIDTH + "d", number);
    }

    private void validateUniqueEmail(Long currentUserId, String email) {
        boolean exists = currentUserId == null
                ? userRepository.existsByEmailIdIgnoreCase(email)
                : userRepository.existsByEmailIdIgnoreCaseAndIdNot(email, currentUserId);

        if (exists) {
            throw new IllegalArgumentException("Email is already in use.");
        }
    }

    private String decodeRequiredBase64Password(String password) {
        return PasswordUtils.decodeBase64(requireTrimmedValue(password, "Password is required"));
    }

    private String decodeOptionalBase64Password(String password) {
        String encoded = normalizeOptionalValue(password);
        return (encoded == null || encoded.isBlank()) ? encoded : PasswordUtils.decodeBase64(encoded);
    }

    private void validateOptionalPassword(String password) {
        if (password == null || password.isBlank()) return;
        PasswordUtils.validate(password);
    }

    private void applyContactInfo(UserAccount user, Long countryId, Long phoneCodeId, String phoneNumber) {
        user.setCountry(countryId != null ? countryRepository.findById(countryId).orElse(null) : null);
        user.setPhoneCode(phoneCodeId != null ? phoneCodeRepository.findById(phoneCodeId).orElse(null) : null);
        user.setPhoneNumber(phoneNumber);
    }

    private Role loadRole(String uniqueName) {
        return roleRepository.findByUniqueNameIgnoreCase(uniqueName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + uniqueName));
    }

    private ManagedUserResponse toManagedUserResponse(UserAccount user, UserAccount actor, boolean includePermissions) {
        return toManagedUserResponse(toUserAccountView(user), actor, includePermissions);
    }

    private ManagedUserResponse toManagedUserResponse(UserAccountView user, UserAccount actor) {
        return toManagedUserResponse(user, actor, true);
    }

    private ManagedUserResponse toManagedUserResponse(UserAccountView user, UserAccount actor, boolean includePermissions) {
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
                includePermissions && canEdit(actor, user),
                includePermissions && canDelete(actor, user),
                user.countryId(),
                user.countryName(),
                user.countryCode(),
                user.countryFlagEmoji(),
                user.phoneCodeId(),
                user.dialCode(),
                user.phoneNumber()
        );
    }

    private UserAccountView toUserAccountView(UserAccount user) {
        Country country = user.getCountry();
        PhoneCode phoneCode = user.getPhoneCode();
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
                user.getLastLogin(),
                country != null ? country.getId() : null,
                country != null ? country.getName() : null,
                country != null ? country.getCode() : null,
                country != null ? country.getFlagEmoji() : null,
                phoneCode != null ? phoneCode.getId() : null,
                phoneCode != null ? phoneCode.getDialCode() : null,
                user.getPhoneNumber()
        );
    }

    private boolean canEdit(UserAccount actor, UserAccountView user) {
        if (isRole(actor, UserConstants.ROLE_ADMIN)) {
            return true;
        }
        if (isRole(actor, UserConstants.ROLE_MANAGER)) {
            return isManagedEmployee(actor, user);
        }
        return false;
    }

    private boolean isSupportedAdminRole(String roleName) {
        return UserConstants.ROLE_ADMIN.equalsIgnoreCase(roleName)
                || UserConstants.ROLE_MANAGER.equalsIgnoreCase(roleName)
                || UserConstants.ROLE_EMPLOYEE.equalsIgnoreCase(roleName);
    }

    private boolean isManagedEmployee(UserAccount actor, UserAccountView user) {
        if (!isRole(user.role(), UserConstants.ROLE_EMPLOYEE)) {
            return false;
        }
        String createdBy = user.createdBy();
        if (createdBy == null || createdBy.isBlank()) {
            return false;
        }
        return createdBy.trim().equalsIgnoreCase(actor.getUsername());
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
        return StringUtils.normalizeRole(roleName);
    }

    private boolean isSameUser(UserAccount actor, UserAccountView user) {
        if (actor.getId() != null && user.id() != null) {
            return actor.getId().equals(user.id());
        }
        return actor.getUsername() != null
                && user.username() != null
                && actor.getUsername().equalsIgnoreCase(user.username());
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
            Instant lastLogin,
            Long countryId,
            String countryName,
            String countryCode,
            String countryFlagEmoji,
            Long phoneCodeId,
            String dialCode,
            String phoneNumber
    ) {
    }
}
