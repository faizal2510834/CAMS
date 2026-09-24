package com.cams.dao;

import com.cams.model.Department;
import com.cams.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DepartmentDAOImpl implements DepartmentDAO {

    @Override
    public List<Department> findAll(String active) throws SQLException {
        List<Department> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT department_id, department_name, active, created_at, updated_at FROM departments ");
        if (active != null && !active.trim().isEmpty()) {
            sql.append("WHERE active = ? ");
        }
        sql.append("ORDER BY department_id ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (active != null && !active.trim().isEmpty()) {
                ps.setString(1, active.trim().toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public Optional<Department> findById(String departmentId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, departmentId);
        }
    }

    @Override
    public Optional<Department> findById(Connection conn, String departmentId) throws SQLException {
        String sql = "SELECT department_id, department_name, active, created_at, updated_at FROM departments WHERE department_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(String departmentId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return existsById(conn, departmentId);
        }
    }

    @Override
    public boolean existsById(Connection conn, String departmentId) throws SQLException {
        String sql = "SELECT 1 FROM departments WHERE department_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean isActive(String departmentId) throws SQLException {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM departments WHERE department_id = ? AND active = 'Y'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, departmentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean insert(Department department) throws SQLException {
        String sql = "INSERT INTO departments (department_id, department_name, active, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, department.getDepartmentId().trim());
            ps.setString(2, department.getDepartmentName().trim());
            ps.setString(3, department.getActive() != null ? department.getActive().trim().toUpperCase() : "Y");
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean update(Department department) throws SQLException {
        String sql = "UPDATE departments SET department_name = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE department_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, department.getDepartmentName().trim());
            ps.setString(2, department.getActive() != null ? department.getActive().trim().toUpperCase() : "Y");
            ps.setString(3, department.getDepartmentId().trim());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deactivate(String departmentId) throws SQLException {
        String sql = "UPDATE departments SET active = 'N', updated_at = CURRENT_TIMESTAMP WHERE department_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, departmentId.trim());
            return ps.executeUpdate() > 0;
        }
    }

    private Department mapRow(ResultSet rs) throws SQLException {
        Department dept = new Department();
        dept.setDepartmentId(rs.getString("department_id"));
        dept.setDepartmentName(rs.getString("department_name"));
        dept.setActive(rs.getString("active"));
        dept.setCreatedAt(rs.getTimestamp("created_at"));
        dept.setUpdatedAt(rs.getTimestamp("updated_at"));
        return dept;
    }
}
