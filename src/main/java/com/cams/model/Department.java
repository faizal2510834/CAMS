package com.cams.model;

import java.sql.Timestamp;

/**
 * Domain entity representing an Academic/Administrative Department in the DEPARTMENTS table.
 */
public class Department {

    private String departmentId;
    private String departmentName;
    private String active; // 'Y' or 'N'
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Department() {
        this.active = "Y";
    }

    public Department(String departmentId, String departmentName, String active) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.active = active != null ? active : "Y";
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
