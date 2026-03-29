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

import java.time.Instant;

@Entity
@Table(name = "user_profile")
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

    protected UserAccount() {
    }

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

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getFullName() {
        return fullName;
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

    public String getRole() {
        if (roleReference != null) {
            return roleReference.getSummaryName();
        }
        return role;
    }

    public String getStoredRole() {
        return role;
    }

    public Role getRoleReference() {
        return roleReference;
    }

    public Long getRoleId() {
        return roleReference == null ? null : roleReference.getId();
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreateDate() {
        return createDate;
    }

    public Instant getUpdateDate() {
        return updateDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public String getGender() {
        return gender;
    }

    public void setPassword(String password) {
        this.userPswd = password;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public void setUsername(String username) {
        this.userName = username;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.emailId = email;
    }

    public void setRole(String role) {
        this.role = role;
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

    public void setCreateDate(Instant createDate) {
        this.createDate = createDate;
    }

    public void setUpdateDate(Instant updateDate) {
        this.updateDate = updateDate;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}
