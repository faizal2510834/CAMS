package com.cams.dao;

import com.cams.model.Category;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for CATEGORIES table operations.
 */
public interface CategoryDAO {

    List<Category> findAll(String active) throws SQLException;

    Optional<Category> findById(String categoryId) throws SQLException;

    Optional<Category> findById(Connection conn, String categoryId) throws SQLException;

    boolean existsById(String categoryId) throws SQLException;

    boolean existsById(Connection conn, String categoryId) throws SQLException;

    boolean isActive(String categoryId) throws SQLException;

    boolean insert(Category category) throws SQLException;

    boolean update(Category category) throws SQLException;

    boolean deactivate(String categoryId) throws SQLException;
}
