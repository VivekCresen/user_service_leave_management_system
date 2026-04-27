package com.cresensolutions.userservice.repository;

import com.cresensolutions.userservice.model.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    @Query("SELECT a FROM AttendanceLog a WHERE a.userAccount.userName = :userName AND a.dateOfLog = :dateOfLog")
    Optional<AttendanceLog> findByUserNameAndDate(@Param("userName") String userName, @Param("dateOfLog") LocalDate dateOfLog);

    @Query("SELECT a FROM AttendanceLog a WHERE a.userAccount.userName = :userName ORDER BY a.dateOfLog DESC, a.checkInTime DESC")
    List<AttendanceLog> findByUserName(@Param("userName") String userName);

    @Query("SELECT a FROM AttendanceLog a WHERE a.dateOfLog = :dateOfLog ORDER BY a.checkInTime DESC")
    List<AttendanceLog> findByDate(@Param("dateOfLog") LocalDate dateOfLog);

}
