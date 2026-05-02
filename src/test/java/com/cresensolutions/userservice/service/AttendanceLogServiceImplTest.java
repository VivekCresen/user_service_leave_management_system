package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.AttendanceLogDto;
import com.cresensolutions.userservice.exception.ResourceNotFoundException;
import com.cresensolutions.userservice.messaging.UserEventPublisher;
import com.cresensolutions.userservice.model.AttendanceLog;
import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.repository.AttendanceLogRepository;
import com.cresensolutions.userservice.repository.UserRepository;
import com.cresensolutions.userservice.service.Impl.AttendanceLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceLogServiceImplTest {

    @Mock private AttendanceLogRepository attendanceLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserEventPublisher eventPublisher;

    @InjectMocks private AttendanceLogServiceImpl attendanceLogService;

    private UserAccount userAccount;
    private AttendanceLog attendanceLog;

    // The real method name used by the impl
    private static final String FIND_ACTIVE = "findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc";

    @BeforeEach
    void setUp() {
        userAccount = new UserAccount("John Doe", "john@example.com", "john", "pass", "EMPLOYEE");
        attendanceLog = new AttendanceLog(userAccount, Instant.now(), LocalDate.now());
    }

    // ── checkIn ───────────────────────────────────────────────────────────────

    @Test
    void checkIn_noExistingLog_createsNewLog() {
        when(userRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(userAccount));
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenReturn(attendanceLog);

        AttendanceLogDto result = attendanceLogService.checkIn("john");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("john");
        verify(attendanceLogRepository).save(any(AttendanceLog.class));
    }

    @Test
    void checkIn_existingActiveLogToday_returnsExistingWithoutSave() {
        when(userRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(userAccount));
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.of(attendanceLog));

        AttendanceLogDto result = attendanceLogService.checkIn("john");

        assertThat(result).isNotNull();
        verify(attendanceLogRepository, never()).save(any());
    }

    @Test
    void checkIn_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUserNameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceLogService.checkIn("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with username: ghost");
    }

    // ── checkOut ──────────────────────────────────────────────────────────────

    @Test
    void checkOut_existingCheckIn_setsCheckOutTime() {
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.of(attendanceLog));
        when(attendanceLogRepository.save(any(AttendanceLog.class))).thenReturn(attendanceLog);

        AttendanceLogDto result = attendanceLogService.checkOut("john");

        assertThat(result).isNotNull();
        verify(attendanceLogRepository).save(attendanceLog);
        assertThat(attendanceLog.getCheckOutTime()).isNotNull();
    }

    @Test
    void checkOut_noActiveCheckInToday_throwsResourceNotFoundException() {
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceLogService.checkOut("john"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active check-in found for today");
    }

    // ── getTodayStatus ────────────────────────────────────────────────────────

    @Test
    void getTodayStatus_activeLogExists_returnsDto() {
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.of(attendanceLog));

        AttendanceLogDto result = attendanceLogService.getTodayStatus("john");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("john");
    }

    @Test
    void getTodayStatus_noActiveLog_fallsBackToLatest() {
        AttendanceLog checkedOut = new AttendanceLog(userAccount, Instant.now(), LocalDate.now());
        checkedOut.setCheckOutTime(Instant.now());

        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.of(checkedOut));

        AttendanceLogDto result = attendanceLogService.getTodayStatus("john");

        assertThat(result).isNotNull();
    }

    @Test
    void getTodayStatus_noLogAtAll_returnsNull() {
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.empty());
        when(attendanceLogRepository
                .findFirstByUserAccountUserNameIgnoreCaseAndDateOfLogOrderByCheckInTimeDesc(
                        "john", LocalDate.now()))
                .thenReturn(Optional.empty());

        AttendanceLogDto result = attendanceLogService.getTodayStatus("john");

        assertThat(result).isNull();
    }

    // ── getAllLogs ────────────────────────────────────────────────────────────

    @Test
    void getAllLogs_returnsMappedList() {
        when(attendanceLogRepository.findAll()).thenReturn(List.of(attendanceLog));

        List<AttendanceLogDto> result = attendanceLogService.getAllLogs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("john");
    }

    @Test
    void getAllLogs_empty_returnsEmptyList() {
        when(attendanceLogRepository.findAll()).thenReturn(List.of());

        assertThat(attendanceLogService.getAllLogs()).isEmpty();
    }

    // ── getLogsByUser ─────────────────────────────────────────────────────────

    @Test
    void getLogsByUser_returnsLogsForUser() {
        when(attendanceLogRepository.findByUserName("john")).thenReturn(List.of(attendanceLog));

        List<AttendanceLogDto> result = attendanceLogService.getLogsByUser("john");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("john");
    }

    @Test
    void getLogsByUser_noLogs_returnsEmptyList() {
        when(attendanceLogRepository.findByUserName("john")).thenReturn(List.of());

        assertThat(attendanceLogService.getLogsByUser("john")).isEmpty();
    }

    // ── getLogsByDate ─────────────────────────────────────────────────────────

    @Test
    void getLogsByDate_returnsLogsForDate() {
        String today = LocalDate.now().toString();
        when(attendanceLogRepository.findByDate(LocalDate.now())).thenReturn(List.of(attendanceLog));

        List<AttendanceLogDto> result = attendanceLogService.getLogsByDate(today);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDateOfLog()).isEqualTo(LocalDate.now());
    }

    @Test
    void getLogsByDate_noLogs_returnsEmptyList() {
        when(attendanceLogRepository.findByDate(LocalDate.of(2026, 1, 1))).thenReturn(List.of());

        assertThat(attendanceLogService.getLogsByDate("2026-01-01")).isEmpty();
    }

    @Test
    void getLogsByDate_multipleLogs_returnsAll() {
        AttendanceLog log2 = new AttendanceLog(userAccount, Instant.now(), LocalDate.now());
        when(attendanceLogRepository.findByDate(LocalDate.now())).thenReturn(List.of(attendanceLog, log2));

        assertThat(attendanceLogService.getLogsByDate(LocalDate.now().toString())).hasSize(2);
    }
}
        