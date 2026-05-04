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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(schema = "user_schema", name = "user_profile")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", length = 100)
    private String companyId;

    @Column(name = "user_name", unique = true)
    private String userName;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "email_id", unique = true, length = 200)
    private String emailId;

    @Column(name = "user_pswd", length = 255)
    private String userPswd;

    @Column(name = "role")
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_user_role"))
    private Role roleReference;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "create_date")
    private Instant createDate;

    @Column(name = "update_date")
    private Instant updateDate;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "gender")
    private String gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", foreignKey = @ForeignKey(name = "fk_user_country"))
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phone_code_id", foreignKey = @ForeignKey(name = "fk_user_phone_code"))
    private PhoneCode phoneCode;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    public UserAccount(String fullName, String email, String username, String password, String role) {
        this.fullName = fullName;
        this.emailId = email;
        this.userName = username;
        this.userPswd = password;
        this.role = role;
        this.active = true;
    }

    public UserAccount(String fullName, String email, String username, String password, Role roleReference) {
        this.fullName = fullName;
        this.emailId = email;
        this.userName = username;
        this.userPswd = password;
        assignRole(roleReference);
        this.active = true;
    }

    public String getEmail() {
        return emailId;
    }

    public String getUsername() {
        return userName;
    }

    public String getPassword() {
        return userPswd;
    }

    public void setUsername(String username) {
        this.userName = username;
    }

    public void setEmail(String email) {
        this.emailId = email;
    }

    public void setPassword(String password) {
        this.userPswd = password;
    }

    public String getRole() {
        if (roleReference != null) {
            return roleReference.getSummaryName();
        }
        return role;
    }

    public String getStoredRole() {
        return role;
    }

    public Long getRoleId() {
        return roleReference == null ? null : roleReference.getId();
    }

    public void assignRole(Role roleReference) {
        if (this.roleReference != null && this.roleReference != roleReference) {
            this.roleReference.removeUser(this);
        }

        this.roleReference = roleReference;
        if (roleReference != null) {
            roleReference.addUser(this);
            this.role = roleReference.getSummaryName();
        }
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}