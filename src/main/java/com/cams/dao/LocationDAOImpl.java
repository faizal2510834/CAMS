package com.cams.dao;

import com.cams.model.Location;
import com.cams.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocationDAOImpl implements LocationDAO {

    @Override
    public List<Location> findAll(String active) throws SQLException {
        List<Location> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT location_id, location_name, active, created_at, updated_at FROM locations ");
        if (active != null && !active.trim().isEmpty()) {
            sql.append("WHERE active = ? ");
        }
        sql.append("ORDER BY location_id ASC");

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
    public Optional<Location> findById(String locationId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, locationId);
        }
    }

    @Override
    public Optional<Location> findById(Connection conn, String locationId) throws SQLException {
        String sql = "SELECT location_id, location_name, active, created_at, updated_at FROM locations WHERE location_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(String locationId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return existsById(conn, locationId);
        }
    }

    @Override
    public boolean existsById(Connection conn, String locationId) throws SQLException {
        String sql = "SELECT 1 FROM locations WHERE location_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean isActive(String locationId) throws SQLException {
        if (locationId == null || locationId.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM locations WHERE location_id = ? AND active = 'Y'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, locationId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean insert(Location location) throws SQLException {
        String sql = "INSERT INTO locations (location_id, location_name, active, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, location.getLocationId().trim());
            ps.setString(2, location.getLocationName().trim());
            ps.setString(3, location.getActive() != null ? location.getActive().trim().toUpperCase() : "Y");
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean update(Location location) throws SQLException {
        String sql = "UPDATE locations SET location_name = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE location_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, location.getLocationName().trim());
            ps.setString(2, location.getActive() != null ? location.getActive().trim().toUpperCase() : "Y");
            ps.setString(3, location.getLocationId().trim());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deactivate(String locationId) throws SQLException {
        String sql = "UPDATE locations SET active = 'N', updated_at = CURRENT_TIMESTAMP WHERE location_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, locationId.trim());
            return ps.executeUpdate() > 0;
        }
    }

    private Location mapRow(ResultSet rs) throws SQLException {
        Location loc = new Location();
        loc.setLocationId(rs.getString("location_id"));
        loc.setLocationName(rs.getString("location_name"));
        loc.setActive(rs.getString("active"));
        loc.setCreatedAt(rs.getTimestamp("created_at"));
        loc.setUpdatedAt(rs.getTimestamp("updated_at"));
        return loc;
    }
}
