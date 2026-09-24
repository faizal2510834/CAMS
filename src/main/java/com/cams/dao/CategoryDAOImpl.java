package com.cams.dao;

import com.cams.model.Category;
import com.cams.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryDAOImpl implements CategoryDAO {

    @Override
    public List<Category> findAll(String active) throws SQLException {
        List<Category> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT category_id, category_name, active, created_at, updated_at FROM categories ");
        if (active != null && !active.trim().isEmpty()) {
            sql.append("WHERE active = ? ");
        }
        sql.append("ORDER BY category_id ASC");

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
    public Optional<Category> findById(String categoryId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, categoryId);
        }
    }

    @Override
    public Optional<Category> findById(Connection conn, String categoryId) throws SQLException {
        String sql = "SELECT category_id, category_name, active, created_at, updated_at FROM categories WHERE category_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(String categoryId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return existsById(conn, categoryId);
        }
    }

    @Override
    public boolean existsById(Connection conn, String categoryId) throws SQLException {
        String sql = "SELECT 1 FROM categories WHERE category_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean isActive(String categoryId) throws SQLException {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM categories WHERE category_id = ? AND active = 'Y'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoryId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean insert(Category category) throws SQLException {
        String sql = "INSERT INTO categories (category_id, category_name, active, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getCategoryId().trim());
            ps.setString(2, category.getCategoryName().trim());
            ps.setString(3, category.getActive() != null ? category.getActive().trim().toUpperCase() : "Y");
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean update(Category category) throws SQLException {
        String sql = "UPDATE categories SET category_name = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE category_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getCategoryName().trim());
            ps.setString(2, category.getActive() != null ? category.getActive().trim().toUpperCase() : "Y");
            ps.setString(3, category.getCategoryId().trim());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deactivate(String categoryId) throws SQLException {
        String sql = "UPDATE categories SET active = 'N', updated_at = CURRENT_TIMESTAMP WHERE category_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoryId.trim());
            return ps.executeUpdate() > 0;
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        Category cat = new Category();
        cat.setCategoryId(rs.getString("category_id"));
        cat.setCategoryName(rs.getString("category_name"));
        cat.setActive(rs.getString("active"));
        cat.setCreatedAt(rs.getTimestamp("created_at"));
        cat.setUpdatedAt(rs.getTimestamp("updated_at"));
        return cat;
    }
}
