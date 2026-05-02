package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.dto.AttendanceLogDto;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.messaging.UserEventPublisher;
import com.cresensolutions.userservice.model.AttendanceLog;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.AttendanceLogRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.service.AttendanceLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceLogServiceImpl implements AttendanceLogService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;

    public AttendanceLogServiceImpl(AttendanceLogRepository attendanceLogRepository,
                                    UserRepository userRepository,
                                    UserEventPublisher eventPublisher) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AttendanceLogDto checkIn(String username) {
        UserAccount userAccount = userRepository.findByUserNameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        LocalDate today = LocalDate.now();
        Optional<AttendanceLog> activeLog = attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(username, today);

        AttendanceLog log;
        if (activeLog.isPresent()) {
            log = activeLog.get();
        } else {
            log = new AttendanceLog(userAccount, Instant.now(), today);
            log = attendanceLogRepository.save(log);
        }

        eventPublisher.publishAttendanceCheckin(username, today);
        return mapToDto(log);
    }

    @Override
    @Transactional
    public AttendanceLogDto checkOut(String username) {
        LocalDate today = LocalDate.now();
        AttendanceLog log = attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(username, today)
                .orElseThrow(() -> new ResourceNotFoundException("No active check-in found for today."));

        log.setCheckOutTime(Instant.now());
        log = attendanceLogRepository.save(log);

        eventPublisher.publishAttendanceCheckout(username, today);
        return mapToDto(log);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceLogDto getTodayStatus(String username) {
        LocalDate today = LocalDate.now();
        Optional<AttendanceLog> activeLog = attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(username, today);

        if (activeLog.isPresent()) {
            return mapToDto(activeLog.get());
        }

        Optional<AttendanceLog> latestLog = attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogOrderByCheckInTimeDesc(username, today);

        return latestLog.map(this::mapToDto).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceLogDto> getAllLogs() {
        return attendanceLogRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceLogDto> getLogsByUser(String username) {
        return attendanceLogRepository.findByUserName(username).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceLogDto> getLogsByDate(String date) {
        LocalDate localDate = LocalDate.parse(date);
        return attendanceLogRepository.findByDate(localDate).stream()
                .map(this::mapToDto)
                .toList();
    }

    private AttendanceLogDto mapToDto(AttendanceLog log) {
        UserAccount user = log.getUserAccount();
        return new AttendanceLogDto(
                log.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                log.getCheckInTime(),
                log.getCheckOutTime(),
                log.getDateOfLog(),
                log.isAutoCheckedOut()
        );
    }
}
