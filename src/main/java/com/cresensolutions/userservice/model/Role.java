package com.cresensolutions.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", unique = true)
    private String roleName;

    @Column(name = "unique_name", unique = true)
    private String uniqueName;

    @Column(name = "role_desc")
    private String roleDescription;

    @Column(name = "create_date")
    private Instant createDate;

    @Column(name = "update_date")
    private Instant updateDate;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    protected Role() {
    }

    public Role(Long id, String roleName, String uniqueName) {
        this.id = id;
        this.roleName = roleName;
        this.uniqueName = uniqueName;
    }

    public Long getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public String getRoleDescription() {
        return roleDescription;
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

    public String getSummaryName() {
        if (uniqueName != null && !uniqueName.isBlank()) {
            return uniqueName;
        }
        return roleName == null ? "" : roleName;
    }

    public boolean matchesUserRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }

        return userRole.equalsIgnoreCase(roleName)
                || userRole.equalsIgnoreCase(uniqueName);
    }
}
