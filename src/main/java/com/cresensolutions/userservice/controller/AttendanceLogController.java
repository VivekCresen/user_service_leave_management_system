package com.cresensolutions.userservice.controller;

import com.cresensolutions.userservice.dto.AttendanceLogDto;
import com.cresensolutions.userservice.service.AttendanceLogService;
import com.cresensolutions.userservice.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users/attendance")
public class AttendanceLogController {

    private final AttendanceLogService attendanceLogService;
    private final SseEmitterService sseEmitterService;

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceLogDto> checkIn(@RequestParam String username) {
        AttendanceLogDto result = attendanceLogService.checkIn(username);
        sseEmitterService.broadcast("ATTENDANCE_UPDATED", "check-in");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/check-out")
    public ResponseEntity<AttendanceLogDto> checkOut(@RequestParam String username) {
        AttendanceLogDto result = attendanceLogService.checkOut(username);
        sseEmitterService.broadcast("ATTENDANCE_UPDATED", "check-out");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<AttendanceLogDto> getTodayStatus(@RequestParam String username) {
        AttendanceLogDto status = attendanceLogService.getTodayStatus(username);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.noContent().build();
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AttendanceLogDto>> getAllLogs() {
        return ResponseEntity.ok(attendanceLogService.getAllLogs());
    }

    @GetMapping("/logs/user")
    public ResponseEntity<List<AttendanceLogDto>> getLogsByUser(@RequestParam String username) {
        return ResponseEntity.ok(attendanceLogService.getLogsByUser(username));
    }

    @GetMapping("/logs/date")
    public ResponseEntity<List<AttendanceLogDto>> getLogsByDate(@RequestParam String date) {
        return ResponseEntity.ok(attendanceLogService.getLogsByDate(date));
    }
}
