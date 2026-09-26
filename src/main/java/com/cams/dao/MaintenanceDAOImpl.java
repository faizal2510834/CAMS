package com.cams.dao;

import com.cams.model.Maintenance;
import com.cams.model.PagedResult;
import com.cams.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MaintenanceDAOImpl implements MaintenanceDAO {

    private static final String BASE_SELECT =
            "SELECT m.maintenance_id, m.asset_id, m.maintenance_date, m.scheduled_date, m.completed_date, " +
            "       m.fault_description, m.technician, m.cost, m.status, m.created_by, m.issue_id, " +
            "       m.created_at, m.updated_at, " +
            "       a.asset_name, a.category, a.department, a.location " +
            "FROM maintenance m " +
            "LEFT JOIN assets a ON m.asset_id = a.asset_id ";

    @Override
    public int create(Connection con, Maintenance m) throws SQLException {
        String sql = "INSERT INTO maintenance (" +
                "maintenance_id, asset_id, maintenance_date, scheduled_date, " +
                "fault_description, technician, cost, status, created_by, issue_id, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getMaintenanceId());
            ps.setString(2, m.getAssetId());
            ps.setDate(3, m.getMaintenanceDate() != null ? m.getMaintenanceDate() : new Date(System.currentTimeMillis()));
            ps.setDate(4, m.getScheduledDate());
            ps.setString(5, m.getFaultDescription());
            ps.setString(6, m.getTechnician());
            ps.setBigDecimal(7, m.getCost() != null ? m.getCost() : BigDecimal.ZERO);
            ps.setString(8, m.getStatus() != null ? m.getStatus() : "SCHEDULED");
            ps.setString(9, m.getCreatedBy());
            ps.setString(10, m.getIssueId());

            return ps.executeUpdate();
        }
    }

    @Override
    public int create(Maintenance maintenance) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return create(con, maintenance);
        }
    }

    @Override
    public int update(Connection con, Maintenance m) throws SQLException {
        String sql = "UPDATE maintenance SET " +
                "scheduled_date = ?, completed_date = ?, fault_description = ?, technician = ?, " +
                "cost = ?, status = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE maintenance_id = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, m.getScheduledDate());
            ps.setDate(2, m.getCompletedDate());
            ps.setString(3, m.getFaultDescription());
            ps.setString(4, m.getTechnician());
            ps.setBigDecimal(5, m.getCost() != null ? m.getCost() : BigDecimal.ZERO);
            ps.setString(6, m.getStatus());
            ps.setString(7, m.getMaintenanceId());

            return ps.executeUpdate();
        }
    }

    @Override
    public int update(Maintenance maintenance) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return update(con, maintenance);
        }
    }

    @Override
    public Optional<Maintenance> findById(Connection con, String maintenanceId) throws SQLException {
        String sql = BASE_SELECT + "WHERE m.maintenance_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, maintenanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Maintenance> findById(String maintenanceId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return findById(con, maintenanceId);
        }
    }

    @Override
    public Optional<Maintenance> findActiveByAssetId(Connection con, String assetId) throws SQLException {
        String sql = BASE_SELECT + "WHERE m.asset_id = ? AND m.status IN ('SCHEDULED', 'IN_PROGRESS', 'REQUIRES_FURTHER_REPAIR') ORDER BY m.created_at DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Maintenance> findActiveByAssetId(String assetId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return findActiveByAssetId(con, assetId);
        }
    }

    @Override
    public PagedResult<Maintenance> search(String status, String assetId, String technician, int page, int size) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim())) {
            where.append(" AND m.status = ? ");
            params.add(status.trim().toUpperCase());
        }

        if (assetId != null && !assetId.trim().isEmpty()) {
            where.append(" AND (UPPER(m.asset_id) LIKE ? OR UPPER(a.asset_name) LIKE ?) ");
            String searchPattern = "%" + assetId.trim().toUpperCase() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        if (technician != null && !technician.trim().isEmpty()) {
            where.append(" AND UPPER(m.technician) LIKE ? ");
            params.add("%" + technician.trim().toUpperCase() + "%");
        }

        int totalItems = 0;
        String countSql = "SELECT COUNT(*) FROM maintenance m LEFT JOIN assets a ON m.asset_id = a.asset_id " + where;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(countSql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getInt(1);
                }
            }
        }

        List<Maintenance> items = new ArrayList<>();
        if (totalItems > 0) {
            int offset = Math.max(0, (page - 1) * size);
            String querySql = BASE_SELECT + where + " ORDER BY m.created_at DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(querySql)) {
                int idx = 1;
                for (Object param : params) {
                    ps.setObject(idx++, param);
                }
                ps.setInt(idx++, offset);
                ps.setInt(idx, size);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                }
            }
        }

        return new PagedResult<>(items, page, size, (long) totalItems);
    }

    @Override
    public BigDecimal getTotalCost(String status, String assetId, String technician) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim())) {
            where.append(" AND m.status = ? ");
            params.add(status.trim().toUpperCase());
        }

        if (assetId != null && !assetId.trim().isEmpty()) {
            where.append(" AND (UPPER(m.asset_id) LIKE ? OR UPPER(a.asset_name) LIKE ?) ");
            String searchPattern = "%" + assetId.trim().toUpperCase() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        if (technician != null && !technician.trim().isEmpty()) {
            where.append(" AND UPPER(m.technician) LIKE ? ");
            params.add("%" + technician.trim().toUpperCase() + "%");
        }

        String costSql = "SELECT NVL(SUM(m.cost), 0) FROM maintenance m LEFT JOIN assets a ON m.asset_id = a.asset_id " + where;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(costSql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private Maintenance mapRow(ResultSet rs) throws SQLException {
        Maintenance m = new Maintenance();
        m.setMaintenanceId(rs.getString("maintenance_id"));
        m.setAssetId(rs.getString("asset_id"));
        m.setMaintenanceDate(rs.getDate("maintenance_date"));
        m.setScheduledDate(rs.getDate("scheduled_date"));
        m.setCompletedDate(rs.getDate("completed_date"));
        m.setFaultDescription(rs.getString("fault_description"));
        m.setTechnician(rs.getString("technician"));
        m.setCost(rs.getBigDecimal("cost"));
        m.setStatus(rs.getString("status"));
        m.setCreatedBy(rs.getString("created_by"));
        m.setIssueId(rs.getString("issue_id"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        m.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Joined fields
        m.setAssetName(rs.getString("asset_name"));
        m.setCategory(rs.getString("category"));
        m.setDepartment(rs.getString("department"));
        m.setLocation(rs.getString("location"));

        return m;
    }
}
