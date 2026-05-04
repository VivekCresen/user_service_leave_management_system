package com.cresensolutions.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLogDto {
    private Long id;
    private String username;
    private String fullName;
    private String role;
    private Instant checkInTime;
    private Instant checkOutTime;
    private LocalDate dateOfLog;
    private boolean autoCheckedOut;
}
