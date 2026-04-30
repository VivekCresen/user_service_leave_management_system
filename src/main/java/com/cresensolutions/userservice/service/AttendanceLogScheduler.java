package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.AttendanceLog;
import com.cresensolutions.userservice.repository.AttendanceLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AttendanceLogScheduler {

    private final AttendanceLogRepository attendanceLogRepository;

    public AttendanceLogScheduler(AttendanceLogRepository attendanceLogRepository) {
        this.attendanceLogRepository = attendanceLogRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoCheckoutAttendance() {
        Instant cutoffTime = Instant.now().minus(12, ChronoUnit.HOURS);
        List<AttendanceLog> logs = attendanceLogRepository.findLogsForAutoCheckout(cutoffTime);
        logs.stream()
                .filter(log -> log.getCheckInTime() != null)
                .forEach(log -> {
                    log.setCheckOutTime(log.getCheckInTime().plus(12, ChronoUnit.HOURS));
                    log.setAutoCheckedOut(true);
                });
        if (!logs.isEmpty()) attendanceLogRepository.saveAll(logs);
    }
}
