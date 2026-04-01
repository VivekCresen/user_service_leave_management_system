package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final Role ADMIN_ROLE = new Role(1L, "Administrator", "ADMIN");
    private static final Role MANAGER_ROLE = new Role(2L, "Manager", "MANAGER");
    private static final Role EMPLOYEE_ROLE = new Role(3L, "Employee", "EMPLOYEE");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private MailProperties mailProperties;

    private UserManagementServiceImpl userManagementService;

    @BeforeEach
    void setUp() {
        userManagementService = new UserManagementServiceImpl(
                userRepository,
                roleRepository,
                passwordEncoder,
                emailService,
                mailProperties,
                Runnable::run
        );
    }

    @Test
    void shouldReturnOnlyEmployeesManagedByCurrentManager() {
        UserAccount manager = createUser("manager-1", "manager1@cresen.com", "MANAGER");
        UserAccount ownEmployee = createUser("employee-1", "employee1@cresen.com", "EMPLOYEE");
        ownEmployee.setCreatedBy("manager-1");
        UserAccount otherManagersEmployee = createUser("employee-2", "employee2@cresen.com", "EMPLOYEE");
        otherManagersEmployee.setCreatedBy("manager-2");
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager-1", "manager-1"))
                .thenReturn(Optional.of(manager));
        when(userRepository.streamAllByOrderByUserNameAsc())
                .thenReturn(Stream.of(admin, ownEmployee, otherManagersEmployee));

        UserDashboardResponse response = userManagementService.getDashboard("manager-1");

        assertEquals(1, response.totalUsers());
        assertEquals(1, response.employeeCount());
        assertIterableEquals(List.of("EMPLOYEE"), response.assignableRoles());
        assertEquals("employee-1", response.users().get(0).username());
    }

    @Test
    void shouldRejectAdminCreationRequestFromAdminDashboard() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        CreateUserRequest request = new CreateUserRequest(
                "admin",
                "CRESEN001",
                "Another Admin",
                "admin-2",
                "admin2@cresen.com",
                "QWRtaW5AMTIz",
                "ADMIN",
                null,
                true,
                "Male"
        );

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> userManagementService.createUser(request)
        );

        assertEquals("Admins can create managers and employees only.", exception.getMessage());
        verify(userRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void shouldRejectManagerUpdatingEmployeeOwnedByAnotherManager() {
        UserAccount actor = createUser("manager-1", "manager1@cresen.com", "MANAGER");
        UserAccount employee = createUser("employee-2", "employee2@cresen.com", "EMPLOYEE");
        employee.setCreatedBy("manager-2");
        UpdateUserRequest request = new UpdateUserRequest(
                "manager-1",
                "CRESEN001",
                "Employee Two",
                "employee-2",
                "employee2@cresen.com",
                "",
                "EMPLOYEE",
                true,
                "Female"
        );

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager-1", "manager-1"))
                .thenReturn(Optional.of(actor));
        when(userRepository.findDetailedById(99L)).thenReturn(Optional.of(employee));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> userManagementService.updateUser(99L, request)
        );

        assertEquals("You do not have permission to manage this user.", exception.getMessage());
    }

    @Test
    void shouldCreateUserAndSendNewAccountEmail() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        CreateUserRequest request = new CreateUserRequest(
                "admin",
                "CRESEN001",
                "New Employee",
                "new.employee",
                "new.employee@cresen.com",
                "VGVtcFBhc3NAMTIz",
                "EMPLOYEE",
                "manager-1",
                true,
                "Female"
        );
        UserAccount existingManager = createUser("manager-1", "manager1@cresen.com", "MANAGER");
        existingManager.setCompanyId("CRESEN001");
        UserAccount existingEmployee = createUser("employee-1", "employee1@cresen.com", "EMPLOYEE");
        existingEmployee.setCompanyId("CRESEN001");
        UserAccount savedUser = createUser("new.employee", "new.employee@cresen.com", "EMPLOYEE");
        savedUser.setCompanyId("CRESEN004");
        savedUser.setFullName("New Employee");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findAllCompanyIds()).thenReturn(List.of("CRESEN001", "CRESEN001"));
        when(userRepository.count()).thenReturn(3L);
        when(userRepository.findByUserNameIgnoreCase("new.employee")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIdIgnoreCase("new.employee@cresen.com")).thenReturn(Optional.empty());
        when(userRepository.findByUserNameIgnoreCase("manager-1")).thenReturn(Optional.of(existingManager));
        when(passwordEncoder.encode("TempPass@123")).thenReturn("encoded-temp-password");
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(EMPLOYEE_ROLE));
        when(userRepository.save(any(UserAccount.class))).thenReturn(savedUser);
        when(mailProperties.forgotPasswordUrl()).thenReturn("http://localhost:4200/forgot-password");

        userManagementService.createUser(request);

        verify(userRepository).save(argThat(user ->
                "CRESEN004".equals(user.getCompanyId()) && "manager-1".equals(user.getCreatedBy())
        ));
        verify(emailService).sendNewUserCreatedEmail(
                eq("new.employee@cresen.com"),
                eq("New Employee"),
                eq(savedUser.getId()),
                eq("CRESEN004"),
                eq("new.employee"),
                eq("EMPLOYEE"),
                eq("http://localhost:4200/forgot-password")
        );
    }

    @Test
    void shouldRejectDuplicateEmailWhenCreatingUser() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        UserAccount existingEmployee = createUser("employee-1", "employee1@cresen.com", "EMPLOYEE");
        ReflectionTestUtils.setField(existingEmployee, "id", 7L);
        CreateUserRequest request = new CreateUserRequest(
                "admin",
                null,
                "New Employee",
                "new.employee",
                "employee1@cresen.com",
                "VGVtcFBhc3NAMTIz",
                "EMPLOYEE",
                "manager-1",
                true,
                "Female"
        );

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findByUserNameIgnoreCase("new.employee")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIdIgnoreCase("employee1@cresen.com")).thenReturn(Optional.of(existingEmployee));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.createUser(request)
        );

        assertEquals("Email is already in use.", exception.getMessage());
        verify(userRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void shouldRequireManagerWhenAdminCreatesEmployee() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        CreateUserRequest request = new CreateUserRequest(
                "admin",
                null,
                "New Employee",
                "new.employee",
                "new.employee@cresen.com",
                "VGVtcFBhc3NAMTIz",
                "EMPLOYEE",
                "   ",
                true,
                "Female"
        );

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findByUserNameIgnoreCase("new.employee")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIdIgnoreCase("new.employee@cresen.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("TempPass@123")).thenReturn("encoded-temp-password");
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(EMPLOYEE_ROLE));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManagementService.createUser(request)
        );

        assertEquals("Manager is required for employee creation.", exception.getMessage());
        verify(userRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void shouldUpdateUserWithoutChangingPasswordWhenPasswordIsBlank() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        UserAccount employee = createUser("employee-1", "employee1@cresen.com", "EMPLOYEE");
        employee.setCompanyId("CRESEN004");
        employee.setFullName("Existing Employee");
        employee.setGender("Male");
        employee.setCreatedBy("admin");
        UpdateUserRequest request = new UpdateUserRequest(
                "admin",
                "CRESEN004",
                "Updated Employee",
                "employee-1",
                "employee1@cresen.com",
                "   ",
                "EMPLOYEE",
                true,
                "Female"
        );

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findDetailedById(11L)).thenReturn(Optional.of(employee));
        when(userRepository.findByUserNameIgnoreCase("employee-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIdIgnoreCase("employee1@cresen.com")).thenReturn(Optional.empty());
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(EMPLOYEE_ROLE));
        when(userRepository.save(employee)).thenReturn(employee);

        userManagementService.updateUser(11L, request);

        assertEquals("encoded-password", employee.getPassword());
        assertEquals("Updated Employee", employee.getFullName());
        assertEquals("Female", employee.getGender());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository).save(employee);
    }

    @Test
    void shouldSendRoleChangedEmailWhenAdminUpdatesUserRole() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        UserAccount employee = createUser("employee-1", "employee1@cresen.com", "EMPLOYEE");
        employee.setCompanyId("CRESEN004");
        employee.setFullName("Existing Employee");
        employee.setGender("Female");
        employee.setCreatedBy("manager-1");
        UpdateUserRequest request = new UpdateUserRequest(
                "admin",
                "CRESEN004",
                "Existing Employee",
                "employee-1",
                "employee1@cresen.com",
                "",
                "MANAGER",
                true,
                "Female"
        );

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findDetailedById(11L)).thenReturn(Optional.of(employee));
        when(userRepository.findByUserNameIgnoreCase("employee-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIdIgnoreCase("employee1@cresen.com")).thenReturn(Optional.empty());
        when(roleRepository.findByUniqueNameIgnoreCase("MANAGER")).thenReturn(Optional.of(MANAGER_ROLE));
        when(userRepository.save(employee)).thenReturn(employee);
        when(mailProperties.loginUrl()).thenReturn("http://localhost:4200/login");

        userManagementService.updateUser(11L, request);

        verify(emailService).sendUserRoleChangedEmail(
                eq("employee1@cresen.com"),
                eq("Existing Employee"),
                eq("employee-1"),
                eq("EMPLOYEE"),
                eq("MANAGER"),
                eq("admin"),
                eq("ADMIN"),
                eq("http://localhost:4200/login")
        );
    }

    @Test
    void shouldSendDeletionEmailWhenAdminDeletesManager() {
        UserAccount admin = createUser("admin", "admin@cresen.com", "ADMIN");
        UserAccount manager = createUser("manager-1", "manager1@cresen.com", "MANAGER");
        manager.setFullName("Manager One");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findDetailedById(15L)).thenReturn(Optional.of(manager));

        userManagementService.deleteUser(15L, "admin");

        verify(userRepository).delete(manager);
        verify(emailService).sendUserDeletedEmail(
                eq("manager1@cresen.com"),
                eq("Manager One"),
                eq("manager-1"),
                eq("MANAGER"),
                eq("admin"),
                eq("ADMIN")
        );
    }

    @Test
    void shouldSendDeletionEmailWhenManagerDeletesEmployee() {
        UserAccount manager = createUser("manager-1", "manager1@cresen.com", "MANAGER");
        manager.setFullName("Manager One");
        UserAccount employee = createUser("employee-1", "employee1@cresen.com", "EMPLOYEE");
        employee.setFullName("Employee One");
        employee.setCreatedBy("manager-1");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("manager-1", "manager-1"))
                .thenReturn(Optional.of(manager));
        when(userRepository.findDetailedById(21L)).thenReturn(Optional.of(employee));

        userManagementService.deleteUser(21L, "manager-1");

        verify(userRepository).delete(employee);
        verify(emailService).sendUserDeletedEmail(
                eq("employee1@cresen.com"),
                eq("Employee One"),
                eq("employee-1"),
                eq("EMPLOYEE"),
                eq("manager-1"),
                eq("MANAGER")
        );
    }

    private UserAccount createUser(String username, String email, String roleName) {
        UserAccount user = new UserAccount(username, email, username, "encoded-password", roleName);
        user.assignRole(toRole(roleName));
        user.setActive(true);
        return user;
    }

    private Role toRole(String roleName) {
        return switch (roleName) {
            case "ADMIN" -> ADMIN_ROLE;
            case "MANAGER" -> MANAGER_ROLE;
            case "EMPLOYEE" -> EMPLOYEE_ROLE;
            default -> null;
        };
    }
}
