package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.CreateUserRequest;
import com.cresensolutions.userservice.dto.UpdateUserRequest;
import com.cresensolutions.userservice.dto.UserDashboardResponse;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

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
        when(userRepository.findAllByOrderByUserNameAsc())
                .thenReturn(List.of(admin, ownEmployee, otherManagersEmployee));

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
                "Admin@123",
                "ADMIN",
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
        when(userRepository.findById(99L)).thenReturn(Optional.of(employee));

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
                "TempPass@123",
                "EMPLOYEE",
                true,
                "Female"
        );
        UserAccount savedUser = createUser("new.employee", "new.employee@cresen.com", "EMPLOYEE");
        savedUser.setCompanyId("CRESEN001");
        savedUser.setFullName("New Employee");

        when(userRepository.findByUserNameIgnoreCaseOrEmailIdIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findByUserNameIgnoreCase("new.employee")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIdIgnoreCase("new.employee@cresen.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("TempPass@123")).thenReturn("encoded-temp-password");
        when(roleRepository.findByUniqueNameIgnoreCase("EMPLOYEE")).thenReturn(Optional.of(EMPLOYEE_ROLE));
        when(userRepository.save(any(UserAccount.class))).thenReturn(savedUser);
        when(mailProperties.forgotPasswordUrl()).thenReturn("http://localhost:4200/forgot-password");

        userManagementService.createUser(request);

        verify(emailService).sendNewUserCreatedEmail(
                eq("new.employee@cresen.com"),
                eq("New Employee"),
                eq(savedUser.getId()),
                eq("CRESEN001"),
                eq("new.employee"),
                eq("TempPass@123"),
                eq("EMPLOYEE"),
                eq("http://localhost:4200/forgot-password")
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
