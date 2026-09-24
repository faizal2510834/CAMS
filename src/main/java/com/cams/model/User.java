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

    public User() {
    }

    public User(Long userId, String name, String role, String department, String username) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.department = department;
        this.username = username;
    }

    public User(Long userId, String name, String role, String department, String username, String password, Timestamp createdAt) {
        this.userId = userId;
        this.name = name;
        this.role = role;
        this.department = department;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
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

    /**
     * Returns a copy of the user with the password hash stripped for safe client transmission.
     */
    public User toSafeUser() {
        User safe = new User(this.userId, this.name, this.role, this.department, this.username);
        safe.setCreatedAt(this.createdAt);
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
                ", createdAt=" + createdAt +
                '}';
    }
}
