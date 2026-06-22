package com.cresensolutions.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "user_schema", name = "user_attendance_log")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_attendance_user"), nullable = false)
    private UserAccount userAccount;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Column(name = "check_out_time")
    private Instant checkOutTime;

    @Column(name = "date_of_log", nullable = false)
    private LocalDate dateOfLog;

    @Column(name = "auto_checked_out", nullable = false)
    private boolean autoCheckedOut = false;

    public AttendanceLog(UserAccount userAccount, Instant checkInTime, LocalDate dateOfLog) {
        this.userAccount = userAccount;
        this.checkInTime = checkInTime;
        this.dateOfLog = dateOfLog;
    }
}
