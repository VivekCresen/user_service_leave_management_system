package com.cresensolutions.userservice.dto;

import java.time.Instant;
import java.time.LocalDate;

public class AttendanceLogDto {
    private Long id;
    private String username;
    private String fullName;
    private String role;
    private Instant checkInTime;
    private Instant checkOutTime;
    private LocalDate dateOfLog;

    public AttendanceLogDto() {}

    public AttendanceLogDto(Long id, String username, String fullName, String role, Instant checkInTime, Instant checkOutTime, LocalDate dateOfLog) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.dateOfLog = dateOfLog;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(Instant checkInTime) {
        this.checkInTime = checkInTime;
    }

    public Instant getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(Instant checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public LocalDate getDateOfLog() {
        return dateOfLog;
    }

    public void setDateOfLog(LocalDate dateOfLog) {
        this.dateOfLog = dateOfLog;
    }
}
