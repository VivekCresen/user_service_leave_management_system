package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.dto.AttendanceLogDto;

import java.util.List;

public interface AttendanceLogService {
    AttendanceLogDto checkIn(String username);
    AttendanceLogDto checkOut(String username);
    AttendanceLogDto getTodayStatus(String username);
    List<AttendanceLogDto> getAllLogs();
    List<AttendanceLogDto> getLogsByUser(String username);
    List<AttendanceLogDto> getLogsByDate(String date);
}
