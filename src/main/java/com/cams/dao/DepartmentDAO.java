package com.cams.dao;

import com.cams.model.Department;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for DEPARTMENTS table operations.
 */
public interface DepartmentDAO {

    List<Department> findAll(String active) throws SQLException;

    Optional<Department> findById(String departmentId) throws SQLException;

    Optional<Department> findById(Connection conn, String departmentId) throws SQLException;

    boolean existsById(String departmentId) throws SQLException;

    boolean existsById(Connection conn, String departmentId) throws SQLException;

    boolean isActive(String departmentId) throws SQLException;

    boolean insert(Department department) throws SQLException;

    boolean update(Department department) throws SQLException;

    boolean deactivate(String departmentId) throws SQLException;
}
