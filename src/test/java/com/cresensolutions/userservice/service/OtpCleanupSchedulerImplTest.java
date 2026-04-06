package com.cresensolutions.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OtpCleanupSchedulerImplTest {

    @Mock
    private OtpService otpService;

    @InjectMocks
    private OtpCleanupSchedulerImpl otpCleanupScheduler;

    @Test
    void testCleanupExpiredOtps() {
        otpCleanupScheduler.cleanupExpiredOtps();

        verify(otpService, times(1)).cleanupExpiredOtps();
    }
}
