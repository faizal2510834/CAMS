package com.cams.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Domain model representing a User in the CAMS system.
 * Based on SRS: User_ID, Name, Role, Department, Username, Password.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String name;
    private String role;
    private String department;
    private String username;
    private transient String password; // Password hash; marked transient to avoid JSON serialization
    private Timestamp createdAt;
    private String active = "Y"; // 'Y' or 'N'
    private String mustChangePassword = "N"; // 'Y' or 'N'

    public User() {
    }

    public User(Long userId, String name, String role, String department, String username) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.department = department;
        this.username = username;
        this.active = "Y";
        this.mustChangePassword = "N";
    }

    public User(Long userId, String name, String role, String department, String username, String password, Timestamp createdAt) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.department = department;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.active = "Y";
        this.mustChangePassword = "N";
    }

    public User(Long userId, String name, String role, String department, String username, String password, Timestamp createdAt, String active, String mustChangePassword) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.department = department;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.active = active != null ? active : "Y";
        this.mustChangePassword = mustChangePassword != null ? mustChangePassword : "N";
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public boolean isActive() {
        return "Y".equalsIgnoreCase(this.active);
    }

    public String getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(String mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isMustChangePassword() {
        return "Y".equalsIgnoreCase(this.mustChangePassword);
    }

    /**
     * Returns a copy of the user with the password hash stripped for safe client transmission.
     */
    public User toSafeUser() {
        User safe = new User(this.userId, this.name, this.role, this.department, this.username);
        safe.setCreatedAt(this.createdAt);
        safe.setActive(this.active);
        safe.setMustChangePassword(this.mustChangePassword);
        return safe;
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", name='" + name + '\'' +
                ", role='" + role + '\'' +
                ", department='" + department + '\'' +
                ", username='" + username + '\'' +
                ", active='" + active + '\'' +
                ", mustChangePassword='" + mustChangePassword + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
