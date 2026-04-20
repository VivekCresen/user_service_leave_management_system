package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.*;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.service.Impl.UserManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private MailProperties mailProperties;
 private final Executor syncExecutor = Runnable::run;

    private UserManagementServiceImpl service;

    private UserAccount adminActor;
    private UserAccount managerActor;
    private UserAccount employeeActor;
    private Role adminRole;
    private Role managerRole;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        service = new UserManagementServiceImpl(
                userRepository, roleRepository, passwordEncoder,
                emailService, mailProperties, syncExecutor);

        adminRole    = new Role(1L, "ADMIN",    "ADMIN");
        managerRole  = new Role(2L, "MANAGER",  "MANAGER");
        employeeRole = new Role(3L, "EMPLOYEE", "EMPLOYEE");

        adminActor   = buildUser(1L, "admin",   "admin@cresensolutions.com",   adminRole,   true);
        managerActor = buildUser(2L, "manager", "manager@cresensolutions.com", managerRole, true);
        employeeActor= buildUser(3L, "emp",     "emp@cresensolutions.com",     employeeRole,true);
    }

    @Test
    void getDashboard_adminActor_returnsAllUsers() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, managerActor, employeeActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        assertThat(dashboard.users()).hasSize(3);
        assertThat(dashboard.canManageUsers()).isTrue();
        assertThat(dashboard.assignableRoles()).containsExactly("MANAGER", "EMPLOYEE");
    }

    @Test
    void getDashboard_managerActor_returnsOwnEmployees() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(employeeActor));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        assertThat(dashboard.users()).hasSize(1);
        assertThat(dashboard.assignableRoles()).containsExactly("EMPLOYEE");
    }

    @Test
    void getDashboard_employeeActor_returnsOnlySelf() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("emp", "emp"))
                .thenReturn(Optional.of(employeeActor));
        when(userRepository.findAllByIdOrderByUserNameAsc(3L))
                .thenReturn(List.of(employeeActor));

        UserDashboardResponse dashboard = service.getDashboard("emp");

        assertThat(dashboard.users()).hasSize(1);
        assertThat(dashboard.canManageUsers()).isFalse();
        assertThat(dashboard.assignableRoles()).isEmpty();
    }

    @Test
    void getDashboard_unknownActor_throwsResourceNotFoundException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDashboard("ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDashboard_inactiveActor_throwsAccessDeniedException() {
        adminActor.setActive(false);
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));

        assertThatThrownBy(() -> service.getDashboard("admin"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createUser_adminCreatesManager_succeeds() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newmgr@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("MANAGER")).thenReturn(Optional.of(managerRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("http://localhost:4200/forgot-password");

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Manager", "newmgr",
                "newmgr@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        ManagedUserResponse response = service.createUser(req);

        assertThat(response.username()).isEqualTo("newmgr");
        assertThat(response.role()).isEqualTo("MANAGER");
    }

    @Test
    void createUser_adminCreatesEmployee_requiresManagerUsername() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.findByUserNameIgnoreCase("manager")).thenReturn(Optional.of(managerActor));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                "manager", true, "Female");

        ManagedUserResponse response = service.createUser(req);

        assertThat(response.username()).isEqualTo("newemp");
    }

    @Test
    void createUser_adminCreatesEmployee_missingManager_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                null, true, "Female");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manager is required");
    }

    @Test
    void createUser_managerCreatesEmployee_succeeds() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        CreateUserRequest req = new CreateUserRequest(
                "manager", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                null, true, "Male");

        ManagedUserResponse response = service.createUser(req);

        assertThat(response.username()).isEqualTo("newemp");
    }

    @Test
    void createUser_managerCreatesManager_throwsAccessDeniedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));

        CreateUserRequest req = new CreateUserRequest(
                "manager", null, "Another Mgr", "anothermgr",
                "anothermgr@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createUser_employeeActor_throwsAccessDeniedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("emp", "emp"))
                .thenReturn(Optional.of(employeeActor));

        CreateUserRequest req = new CreateUserRequest(
                "emp", null, "Someone", "someone",
                "someone@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createUser_duplicateUsername_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("admin")).thenReturn(true);

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "Dup", "admin",
                "dup@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is already in use");
    }

    @Test
    void createUser_duplicateEmail_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("admin@cresensolutions.com")).thenReturn(true);

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "Dup", "newmgr",
                "admin@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is already in use");
    }

    @Test
    void createUser_invalidBase64Password_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New", "newuser",
                "new@cresensolutions.com", "not-base64!!!", "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void updateUser_adminUpdatesEmployee_succeeds() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        ManagedUserResponse response = service.updateUser(10L, req);

        assertThat(response.username()).isEqualTo("emp2");
    }

    @Test
    void updateUser_adminChangesRole_sendsRoleChangedEmail() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("MANAGER")).thenReturn(Optional.of(managerRole));
        when(mailProperties.loginUrl()).thenReturn("http://localhost:4200/login");

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "MANAGER", true, "Male");

        service.updateUser(10L, req);

        verify(emailService).sendUserRoleChangedEmail(
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void updateUser_targetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "X", "x", "x@cresensolutions.com",
                null, "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(99L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateUser_managerUpdatesNonManagedEmployee_throwsAccessDeniedException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("other_manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        UpdateUserRequest req = new UpdateUserRequest(
                "manager", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateUser_withNewPassword_encodesPassword() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$10$newHash");

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", base64("NewPass1!"), "EMPLOYEE", true, "Male");

        service.updateUser(10L, req);

        verify(passwordEncoder).encode("NewPass1!");
    }

    @Test
    void deleteUser_adminDeletesEmployee_succeeds() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        service.deleteUser(10L, "admin");

        verify(userRepository).delete(target);
        verify(emailService).sendUserDeletedEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void deleteUser_selfDelete_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(1L)).thenReturn(Optional.of(adminActor));

        UserAccount selfTarget = buildUser(1L, "admin", "admin@cresensolutions.com", adminRole, true);
        when(userRepository.findDetailedById(1L)).thenReturn(Optional.of(selfTarget));

        assertThatThrownBy(() -> service.deleteUser(1L, "admin"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteUser_targetNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(99L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUser_managerDeletesManagedEmployee_succeeds() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        service.deleteUser(10L, "manager");

        verify(userRepository).delete(target);
    }

    @Test
    void deleteUser_managerDeletesNonManagedEmployee_throwsAccessDeniedException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("other_manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.deleteUser(10L, "manager"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createUser_adminCreatesEmployee_inactiveManager_throwsIllegalArgumentException() {
        managerActor.setActive(false);
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.findByUserNameIgnoreCase("manager")).thenReturn(Optional.of(managerActor));

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                "manager", true, "Female");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active");
    }

    @Test
    void createUser_adminCreatesEmployee_nonManagerUser_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.findByUserNameIgnoreCase("emp")).thenReturn(Optional.of(employeeActor));

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                "emp", true, "Female");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manager role");
    }

    @Test
    void createUser_adminCreatesEmployee_managerNotFound_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.findByUserNameIgnoreCase("ghost")).thenReturn(Optional.empty());

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                "ghost", true, "Female");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createUser_companyIdCollision_incrementsUntilUnique() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newmgr@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("MANAGER")).thenReturn(Optional.of(managerRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase("CRESEN004")).thenReturn(true);
        when(userRepository.existsByCompanyIdIgnoreCase("CRESEN005")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Manager", "newmgr",
                "newmgr@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        ManagedUserResponse response = service.createUser(req);

        assertThat(response.companyId()).isEqualTo("CRESEN005");
    }

    @Test
    void createUser_weakPassword_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newmgr@cresensolutions.com")).thenReturn(false);

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Manager", "newmgr",
                "newmgr@cresensolutions.com", base64("weakpass"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUser_tooShortPassword_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newmgr@cresensolutions.com")).thenReturn(false);

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Manager", "newmgr",
                "newmgr@cresensolutions.com", base64("Ab1!"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

    @Test
    void updateUser_invalidBase64Password_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", "not-base64!!!", "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void deleteUser_managerDeletesOwnAccount_throwsIllegalArgumentException() {
        UserAccount selfAsEmployee = buildUser(2L, "manager", "manager@cresensolutions.com", employeeRole, true);
        selfAsEmployee.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(2L)).thenReturn(Optional.of(selfAsEmployee));

        assertThatThrownBy(() -> service.deleteUser(2L, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot delete your own account");
    }

    @Test
    void getDashboard_metricsCalculation_correctCounts() {
        UserAccount inactiveEmp = buildUser(4L, "inactiveEmp", "inactive@cresensolutions.com", employeeRole, false);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, managerActor, employeeActor, inactiveEmp));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        assertThat(dashboard.totalUsers()).isEqualTo(4);
        assertThat(dashboard.activeUsers()).isEqualTo(3);
        assertThat(dashboard.inactiveUsers()).isEqualTo(1);
        assertThat(dashboard.adminCount()).isEqualTo(1);
        assertThat(dashboard.managerCount()).isEqualTo(1);
        assertThat(dashboard.employeeCount()).isEqualTo(2);
    }

    @Test
    void updateUser_weakPassword_throwsIllegalArgumentException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", base64("weakpass"), "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateUser_tooShortPassword_throwsIllegalArgumentException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", base64("Ab1!"), "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

    @Test
    void updateUser_employeeTriesToUpdate_throwsAccessDeniedException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("emp", "emp"))
                .thenReturn(Optional.of(employeeActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        UpdateUserRequest req = new UpdateUserRequest(
                "emp", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteUser_employeeTriesToDelete_throwsAccessDeniedException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("emp", "emp"))
                .thenReturn(Optional.of(employeeActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.deleteUser(10L, "emp"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createUser_adminCreatesAdminRole_throwsAccessDeniedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "Another Admin", "admin2",
                "admin2@cresensolutions.com", base64("Secret1!"), "ADMIN",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Admins can create managers and employees only");
    }

    @Test
    void updateUser_duplicateUsername_throwsIllegalArgumentException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("taken", 10L)).thenReturn(true);

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "taken",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is already in use");
    }

    @Test
    void updateUser_duplicateEmail_throwsIllegalArgumentException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("taken@cresensolutions.com", 10L)).thenReturn(true);

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "taken@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is already in use");
    }


    @Test
    void getDashboard_adminActor_usesDetailedRepo_andAssignableRoles() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, managerActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        assertThat(dashboard.assignableRoles()).containsExactly("MANAGER", "EMPLOYEE");
        assertThat(dashboard.canManageUsers()).isTrue();
    }


    @Test
    void createUser_employeeActor_cannotManage_throwsAccessDeniedException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("emp", "emp"))
                .thenReturn(Optional.of(employeeActor));

        CreateUserRequest req = new CreateUserRequest(
                "emp", null, "New", "newuser",
                "new@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("permission to create");
    }

    @Test
    void createUser_managerActor_setsCreatedByToManager() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        CreateUserRequest req = new CreateUserRequest(
                "manager", null, "New Emp", "newemp",
                "newemp@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                null, true, "Male");

        ManagedUserResponse response = service.createUser(req);

        assertThat(response.username()).isEqualTo("newemp");
    }

    @Test
    void updateUser_adminUpdatesEmployee_withValidPassword_encodesIt() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$10$newHash");

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", base64("NewPass1!"), "EMPLOYEE", true, "Male");

        service.updateUser(10L, req);

        verify(passwordEncoder).encode("NewPass1!");
    }

    @Test
    void updateUser_managerUpdatesManagedEmployee_succeeds() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "manager", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        ManagedUserResponse response = service.updateUser(10L, req);

        assertThat(response.username()).isEqualTo("emp2");
    }

    @Test
    void getDashboard_admin_canEditNonAdminUsers() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, managerActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        ManagedUserResponse managerEntry = dashboard.users().stream()
                .filter(u -> u.username().equals("manager"))
                .findFirst().orElseThrow();
        assertThat(managerEntry.canEdit()).isTrue();
        assertThat(managerEntry.canDelete()).isTrue();
    }

    @Test
    void getDashboard_manager_canEditManagedEmployee() {
        employeeActor.setCreatedBy("manager");
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(employeeActor));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        ManagedUserResponse empEntry = dashboard.users().get(0);
        assertThat(empEntry.canEdit()).isTrue();
    }

    @Test
    void getDashboard_manager_cannotEditUnmanagedEmployee() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(employeeActor));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        ManagedUserResponse empEntry = dashboard.users().get(0);
        assertThat(empEntry.canEdit()).isFalse();
    }

    @Test
    void deleteUser_sameUserByUsername_throwsIllegalArgumentException() {

        UserAccount actorNoId = new UserAccount();
        actorNoId.setUsername("manager");
        actorNoId.setEmail("manager@cresensolutions.com");
        actorNoId.setActive(true);
        actorNoId.assignRole(managerRole);

        UserAccount targetNoId = new UserAccount();
        targetNoId.setUsername("manager");
        targetNoId.setEmail("manager@cresensolutions.com");
        targetNoId.setActive(true);
        targetNoId.assignRole(employeeRole);
        targetNoId.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(actorNoId));
        when(userRepository.findDetailedById(99L)).thenReturn(Optional.of(targetNoId));

        assertThatThrownBy(() -> service.deleteUser(99L, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot delete your own account");
    }

    @Test
    void getDashboard_userWithNullRole_countedCorrectly() {
        UserAccount noRoleUser = new UserAccount();
        noRoleUser.setUsername("norole");
        noRoleUser.setEmail("norole@cresensolutions.com");
        noRoleUser.setActive(true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, noRoleUser));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        assertThat(dashboard.totalUsers()).isEqualTo(2);
    }

    @Test
    void updateUser_nullPassword_skipsValidation() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        ManagedUserResponse response = service.updateUser(10L, req);

        assertThat(response.username()).isEqualTo("emp2");
        verify(passwordEncoder, never()).encode(anyString());
    }
    
    @Test
    void createUser_adminCreatesEmployee_responseHasCanEditTrue() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newemp3")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newemp3@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.findByUserNameIgnoreCase("manager")).thenReturn(Optional.of(managerActor));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Emp3", "newemp3",
                "newemp3@cresensolutions.com", base64("Secret1!"), "EMPLOYEE",
                "manager", true, "Male");

        ManagedUserResponse response = service.createUser(req);
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void updateUser_adminUpdatesEmployee_responseHasCanEditTrue() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        ManagedUserResponse response = service.updateUser(10L, req);
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void updateUser_adminUpdatesAdminTarget_responseHasCanEditFalse() {
        UserAccount adminTarget = buildUser(10L, "admin2", "admin2@cresensolutions.com", adminRole, true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(adminTarget));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Admin Two", "admin2",
                "admin2@cresensolutions.com", null, "ADMIN", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getDashboard_admin_seesNonAdminUsers_canEditTrue() {
        employeeActor.setCreatedBy("admin");
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, employeeActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        ManagedUserResponse empEntry = dashboard.users().stream()
                .filter(u -> u.username().equals("emp"))
                .findFirst().orElseThrow();
        assertThat(empEntry.canEdit()).isTrue();
    }

    @Test
    void updateUser_managerUpdatesNonEmployee_throwsAccessDeniedException() {
        UserAccount anotherMgr = buildUser(10L, "mgr2", "mgr2@cresensolutions.com", managerRole, true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(anotherMgr));

        UpdateUserRequest req = new UpdateUserRequest(
                "manager", null, "Mgr Two", "mgr2",
                "mgr2@cresensolutions.com", null, "MANAGER", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deleteUser_managerDeletesEmployeeWithBlankCreatedBy_throwsAccessDeniedException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("   ");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.deleteUser(10L, "manager"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getDashboard_manager_seesManagerInList_cannotEdit() {
        UserAccount anotherMgr = buildUser(10L, "mgr2", "mgr2@cresensolutions.com", managerRole, true);
        anotherMgr.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(anotherMgr));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        assertThat(dashboard.users().get(0).canEdit()).isFalse();
    }

    @Test
    void getDashboard_manager_employeeWithBlankCreatedBy_cannotEdit() {
        UserAccount emp = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        emp.setCreatedBy("   ");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(emp));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        assertThat(dashboard.users().get(0).canEdit()).isFalse();
    }

    @Test
    void deleteUser_actorHasIdTargetHasNullId_usernameMatch_throwsIllegalArgumentException() {
        UserAccount targetNoId = new UserAccount();
        targetNoId.setUsername("manager");
        targetNoId.setEmail("manager@cresensolutions.com");
        targetNoId.setActive(true);
        targetNoId.assignRole(employeeRole);
        targetNoId.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(99L)).thenReturn(Optional.of(targetNoId));

        assertThatThrownBy(() -> service.deleteUser(99L, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot delete your own account");
    }

    @Test
    void getDashboard_admin_selfEntryInList_cannotDeleteSelf() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, managerActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");
        ManagedUserResponse managerEntry = dashboard.users().stream()
                .filter(u -> u.username().equals("manager"))
                .findFirst().orElseThrow();
        assertThat(managerEntry.canDelete()).isTrue();
    }

    @Test
    void getDashboard_admin_userWithNullRole_normalizeRoleNameHandled() {
        UserAccount noRole = new UserAccount();
        noRole.setUsername("norole");
        noRole.setEmail("norole@cresensolutions.com");
        noRole.setActive(true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, noRole));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        assertThat(dashboard.totalUsers()).isEqualTo(2);
    }

    @Test
    void updateUser_nullPassword_skipsPasswordValidation() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        ManagedUserResponse response = service.updateUser(10L, req);

        assertThat(response.username()).isEqualTo("emp2");
        verify(passwordEncoder, never()).encode(anyString());
    }


    @Test
    void createUser_blankFullName_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "   ", "newuser",
                "new@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name is required");
    }


    @Test
    void updateUser_nullPasswordField_decodesAsNull() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        service.updateUser(10L, req);

        verify(passwordEncoder, never()).encode(anyString());
    }

    private UserAccount buildUser(Long id, String username, String email, Role role, boolean active) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName("Full " + username);
        user.setActive(active);
        user.assignRole(role);
       try {
            var field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }


    @Test
    void getDashboard_admin_cannotEditAdminTarget() {
        UserAccount anotherAdmin = buildUser(5L, "admin2", "admin2@cresensolutions.com", adminRole, true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, anotherAdmin));

        UserDashboardResponse dashboard = service.getDashboard("admin");
        ManagedUserResponse admin2Entry = dashboard.users().stream()
                .filter(u -> u.username().equals("admin2"))
                .findFirst().orElseThrow();
        assertThat(admin2Entry.canEdit()).isFalse();
        assertThat(admin2Entry.canDelete()).isFalse();
    }

    @Test
    void getDashboard_actorResponse_includePermissionsFalse_canEditFalse() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");
        assertThat(dashboard.actor().canEdit()).isFalse();
        assertThat(dashboard.actor().canDelete()).isFalse();
    }

    @Test
    void updateUser_managerTriesToUpdateAnotherManager_throwsAccessDeniedException() {
        UserAccount anotherManager = buildUser(10L, "mgr2", "mgr2@cresensolutions.com", managerRole, true);

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(anotherManager));

        UpdateUserRequest req = new UpdateUserRequest(
                "manager", null, "Mgr Two", "mgr2",
                "mgr2@cresensolutions.com", null, "MANAGER", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(AccessDeniedException.class);
    }
    @Test
    void deleteUser_managerDeletesEmployeeWithNullCreatedBy_throwsAccessDeniedException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.deleteUser(10L, "manager"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getDashboard_manager_nonEmployeeInList_cannotEdit() {
        UserAccount anotherMgr = buildUser(10L, "mgr2", "mgr2@cresensolutions.com", managerRole, true);
        anotherMgr.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(anotherMgr));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        ManagedUserResponse entry = dashboard.users().get(0);
        assertThat(entry.canEdit()).isFalse();
    }

    @Test
    void getDashboard_manager_employeeWithNullCreatedBy_cannotEdit() {
        UserAccount emp = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(emp));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        ManagedUserResponse entry = dashboard.users().get(0);
        assertThat(entry.canEdit()).isFalse();
    }

    @Test
    void getDashboard_userWithNullRole_handledGracefully() {
        UserAccount noRole = new UserAccount();
        noRole.setUsername("norole");
        noRole.setEmail("norole@cresensolutions.com");
        noRole.setActive(true);
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, noRole));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        assertThat(dashboard.totalUsers()).isEqualTo(2);
    }

    @Test
    void deleteUser_actorWithNullId_sameUsernameAsSelf_throwsIllegalArgumentException() {
        UserAccount actorNoId = new UserAccount();
        actorNoId.setUsername("manager");
        actorNoId.setEmail("manager@cresensolutions.com");
        actorNoId.setActive(true);
        actorNoId.assignRole(managerRole);

        UserAccount targetNoId = new UserAccount();
        targetNoId.setUsername("manager");
        targetNoId.setEmail("manager@cresensolutions.com");
        targetNoId.setActive(true);
        targetNoId.assignRole(employeeRole);
        targetNoId.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(actorNoId));
        when(userRepository.findDetailedById(99L)).thenReturn(Optional.of(targetNoId));

        assertThatThrownBy(() -> service.deleteUser(99L, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot delete your own account");
    }
    @Test
    void getDashboard_admin_canDeleteNonSelfUser() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, managerActor));

        UserDashboardResponse dashboard = service.getDashboard("admin");

        ManagedUserResponse managerEntry = dashboard.users().stream()
                .filter(u -> u.username().equals("manager"))
                .findFirst().orElseThrow();
        assertThat(managerEntry.canDelete()).isTrue();
    }

    @Test
    void updateUser_blankPassword_skipsValidation() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        String blankBase64 = Base64.getEncoder().encodeToString("   ".getBytes());
        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", blankBase64, "EMPLOYEE", true, "Male");

        service.updateUser(10L, req);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createUser_adminCreatesManager_resolveOwnerUsernameReturnsAdmin() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr2")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newmgr2@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("MANAGER")).thenReturn(Optional.of(managerRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, "New Mgr2", "newmgr2",
                "newmgr2@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        ManagedUserResponse response = service.createUser(req);
        assertThat(response.createdBy()).isEqualTo("admin");
    }

    @Test
    void createUser_nullFullName_throwsIllegalArgumentException() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));

        CreateUserRequest req = new CreateUserRequest(
                "admin", null, null, "newuser",
                "new@cresensolutions.com", base64("Secret1!"), "MANAGER",
                null, true, "Male");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name is required");
    }
    @Test
    void updateUser_tooLongPassword_throwsIllegalArgumentException() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        String longPass = "Aa1!" + "x".repeat(260);
        UpdateUserRequest req = new UpdateUserRequest(
                "admin", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", base64(longPass), "EMPLOYEE", true, "Male");

        assertThatThrownBy(() -> service.updateUser(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 8 and 255");
    }

    @Test
    void deleteUser_runAfterCommit_withActiveSynchronization_registersCallback() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteUser(10L, "admin");
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(s -> s.afterCommit());
            verify(emailService).sendUserDeletedEmail(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createUser_runAfterCommit_withActiveSynchronization_registersCallback() {
        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.existsByUserNameIgnoreCase("newmgr3")).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCase("newmgr3@cresensolutions.com")).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("MANAGER")).thenReturn(Optional.of(managerRole));
        when(userRepository.findHighestCompanyIdNumber(anyString(), anyInt())).thenReturn(3);
        when(userRepository.existsByCompanyIdIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mailProperties.forgotPasswordUrl()).thenReturn("");

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.createUser(new CreateUserRequest(
                    "admin", null, "New Mgr3", "newmgr3",
                    "newmgr3@cresensolutions.com", base64("Secret1!"), "MANAGER",
                    null, true, "Male"));
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(s -> s.afterCommit());
            verify(emailService).sendNewUserCreatedEmail(
                    anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
    @Test
    void updateUser_managerUpdatesManagedEmployee_canEditTrue() {
        UserAccount target = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        target.setCreatedBy("manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findDetailedById(10L)).thenReturn(Optional.of(target));
        when(userRepository.existsByUserNameIgnoreCaseAndIdNot("emp2", 10L)).thenReturn(false);
        when(userRepository.existsByEmailIdIgnoreCaseAndIdNot("emp2@cresensolutions.com", 10L)).thenReturn(false);
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(employeeRole));

        UpdateUserRequest req = new UpdateUserRequest(
                "manager", null, "Emp Two", "emp2",
                "emp2@cresensolutions.com", null, "EMPLOYEE", true, "Male");

        ManagedUserResponse response = service.updateUser(10L, req);

        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void updateUser_managerUpdatesUnmanagedEmployee_canEditFalse_inResponse() {
        UserAccount emp = buildUser(10L, "emp2", "emp2@cresensolutions.com", employeeRole, true);
        emp.setCreatedBy("other_manager");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(managerActor));
        when(userRepository.findAllByCreatedByIgnoreCaseAndRoleIgnoreCaseOrderByUserNameAsc("manager", "EMPLOYEE"))
                .thenReturn(List.of(emp));

        UserDashboardResponse dashboard = service.getDashboard("manager");

        assertThat(dashboard.users().get(0).canEdit()).isFalse();
    }
    @Test
    void getDashboard_admin_userWithNullId_isSameUserFallsToUsername() {
        UserAccount noIdUser = new UserAccount();
        noIdUser.setUsername("admin");
        noIdUser.setEmail("admin2@cresensolutions.com");
        noIdUser.setActive(true);
        noIdUser.assignRole(employeeRole);
        noIdUser.setCreatedBy("admin");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(adminActor));
        when(userRepository.findAllDetailedByOrderByUserNameAsc())
                .thenReturn(List.of(adminActor, noIdUser));

        UserDashboardResponse dashboard = service.getDashboard("admin");
        ManagedUserResponse noIdEntry = dashboard.users().stream()
                .filter(u -> u.email().equals("admin2@cresensolutions.com"))
                .findFirst().orElseThrow();
        assertThat(noIdEntry.canEdit()).isTrue();
        assertThat(noIdEntry.canDelete()).isFalse();
    }
}
