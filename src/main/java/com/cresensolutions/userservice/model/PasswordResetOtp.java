package com.cresensolutions.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "otp",
        indexes = {
                @Index(name = "idx_otp_email_id", columnList = "email_id"),
                @Index(name = "idx_otp_expiry_time", columnList = "expiry_time")
        }
)
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_id", unique = true)
    private String emailId;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "expiry_time")
    private Instant expiryTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_otp_user"))
    private UserAccount user;

    protected PasswordResetOtp() {
    }

    public PasswordResetOtp(String emailId, String otpCode, Instant expiryTime) {
        this.emailId = emailId;
        this.otpCode = otpCode;
        this.expiryTime = expiryTime;
    }

    public PasswordResetOtp(UserAccount user, String otpCode, Instant expiryTime) {
        this.user = user;
        this.emailId = user == null ? null : user.getEmail();
        this.otpCode = otpCode;
        this.expiryTime = expiryTime;
    }

    public Long getId() {
        return id;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public Instant getExpiryTime() {
        return expiryTime;
    }

    public UserAccount getUser() {
        return user;
    }

    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public void setExpiryTime(Instant expiryTime) {
        this.expiryTime = expiryTime;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public void setUser(UserAccount user) {
        this.user = user;
        this.emailId = user == null ? this.emailId : user.getEmail();
    }
}
