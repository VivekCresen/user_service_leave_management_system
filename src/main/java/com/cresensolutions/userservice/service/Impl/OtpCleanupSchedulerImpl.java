package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.service.OtpCleanupScheduler;
import com.cresensolutions.userservice.service.OtpService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OtpCleanupSchedulerImpl implements OtpCleanupScheduler {

    private final OtpService otpService;

    public OtpCleanupSchedulerImpl(OtpService otpService) {
        this.otpService = otpService;
    }

    @Override
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredOtps() {
        otpService.cleanupExpiredOtps();
    }
}
