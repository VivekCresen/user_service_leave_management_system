package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.RoleSummaryResponse;
import com.cresensolutions.userservice.model.Role;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.RoleRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleSummaryServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationAuditService authenticationAuditService;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldBuildRoleSummaryUsingRoleTableAndUserProfiles() {
        Role adminRole = new Role(1L, "Administrator", "ADMIN");
        Role managerRole = new Role(2L, "Manager", "MANAGER");

        UserAccount adminOne = new UserAccount("Admin One", "admin1@cresen.com", "admin1", "secret", "ADMIN");
        UserAccount adminTwo = new UserAccount("Admin Two", "admin2@cresen.com", "admin2", "secret", "Administrator");
        UserAccount managerOne = new UserAccount("Manager One", "manager1@cresen.com", "manager1", "secret", "MANAGER");
        adminOne.assignRole(adminRole);
        adminTwo.assignRole(adminRole);
        managerOne.assignRole(managerRole);

        when(roleRepository.findAllByOrderByIdAsc()).thenReturn(List.of(adminRole, managerRole));
        when(userRepository.streamAllByOrderByUserNameAsc()).thenReturn(Stream.of(adminOne, adminTwo, managerOne));

        List<RoleSummaryResponse> summary = authService.fetchRoleSummary();

        assertEquals(2, summary.size());
        assertEquals("ADMIN", summary.get(0).roleName());
        assertEquals(2, summary.get(0).userCount());
        assertEquals(List.of("admin1", "admin2"), summary.get(0).usernames());
        assertEquals("MANAGER", summary.get(1).roleName());
        assertEquals(1, summary.get(1).userCount());
    }
}
