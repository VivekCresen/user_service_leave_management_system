package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordMigrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private PasswordMigrationServiceImpl passwordMigrationService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testRun_WithRequiresHashUpgrade() {
        UserAccount user1 = new UserAccount();
        user1.setPassword("plainTextPassword");

        when(userRepository.findAll()).thenReturn(Collections.singletonList(user1));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedPasswordHashMockingBcryptString");

        passwordMigrationService.run(applicationArguments);

        verify(passwordEncoder, times(1)).encode("plainTextPassword");
    }

    @Test
    void testRun_WithNoUpgradeRequired() {
        UserAccount user1 = new UserAccount();
        user1.setPassword("$2a$10$01234567890123456789012345678901234567890123456789012");

        when(userRepository.findAll()).thenReturn(Collections.singletonList(user1));

        passwordMigrationService.run(applicationArguments);

        verify(passwordEncoder, never()).encode(anyString());
    }
    
    @Test
    void testRun_WithNullPassword() {
        UserAccount user1 = new UserAccount();
        user1.setPassword(null);

        when(userRepository.findAll()).thenReturn(Collections.singletonList(user1));

        passwordMigrationService.run(applicationArguments);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void testRun_WithEmptyUserList() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        passwordMigrationService.run(applicationArguments);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void testRun_WithMultipleUsers_OnlyUpgradesNonBcrypt() {
        UserAccount user1 = new UserAccount();
        user1.setPassword("plainText");

        UserAccount user2 = new UserAccount();
        user2.setPassword("$2a$10$01234567890123456789012345678901234567890123456789012");

        UserAccount user3 = new UserAccount();
        user3.setPassword(null);

        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2, user3));
        when(passwordEncoder.encode("plainText")).thenReturn("$2a$10$encodedHash");

        passwordMigrationService.run(applicationArguments);

        verify(passwordEncoder, times(1)).encode("plainText");
    }
}
