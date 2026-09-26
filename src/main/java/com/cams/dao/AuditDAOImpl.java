package com.cams.dao;

import com.cams.model.AuditQueryCriteria;
import com.cams.model.AuditRecord;
import com.cams.model.AuditSummary;
import com.cams.model.PagedResult;
import com.cams.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * JDBC implementation of AuditDAO using Oracle Database syntax.
 */
public class AuditDAOImpl implements AuditDAO {

    private static final Logger LOGGER = Logger.getLogger(AuditDAOImpl.class.getName());

    @Override
    public boolean insert(Connection conn, AuditRecord record) throws SQLException {
        String sql = "INSERT INTO inventory_audits (" +
                "audit_id, asset_id, audit_date, verified_by, status, remarks, created_at" +
                ") VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getAuditId());
            ps.setString(2, record.getAssetId());
            ps.setString(3, record.getVerifiedBy());
            ps.setString(4, record.getStatus());
            ps.setString(5, record.getRemarks());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean insert(AuditRecord record) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, record);
        }
    }

    @Override
    public Optional<AuditRecord> findById(String auditId) throws SQLException {
        if (auditId == null || auditId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT ia.audit_id, ia.asset_id, ia.audit_date, ia.verified_by, ia.status, ia.remarks, ia.created_at, " +
                "a.asset_name, a.category, a.department, a.location, a.status AS asset_status " +
                "FROM inventory_audits ia " +
                "JOIN assets a ON ia.asset_id = a.asset_id " +
                "WHERE ia.audit_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auditId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToAuditRecord(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public PagedResult<AuditRecord> search(AuditQueryCriteria criteria) throws SQLException {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (criteria.getAssetId() != null && !criteria.getAssetId().trim().isEmpty()) {
            whereClause.append(" AND (UPPER(ia.asset_id) LIKE ? OR UPPER(a.asset_name) LIKE ?) ");
            String kw = "%" + criteria.getAssetId().trim().toUpperCase() + "%";
            params.add(kw);
            params.add(kw);
        }

        if (criteria.getStatus() != null && !criteria.getStatus().trim().isEmpty()) {
            whereClause.append(" AND ia.status = ? ");
            params.add(criteria.getStatus().trim().toUpperCase());
        }

        if (criteria.getDepartment() != null && !criteria.getDepartment().trim().isEmpty()) {
            whereClause.append(" AND UPPER(a.department) = ? ");
            params.add(criteria.getDepartment().trim().toUpperCase());
        }

        if (criteria.getVerifiedBy() != null && !criteria.getVerifiedBy().trim().isEmpty()) {
            whereClause.append(" AND UPPER(ia.verified_by) = ? ");
            params.add(criteria.getVerifiedBy().trim().toUpperCase());
        }

        if (criteria.getStartDate() != null && !criteria.getStartDate().trim().isEmpty()) {
            whereClause.append(" AND ia.audit_date >= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(criteria.getStartDate().trim());
        }

        if (criteria.getEndDate() != null && !criteria.getEndDate().trim().isEmpty()) {
            whereClause.append(" AND ia.audit_date < TO_DATE(?, 'YYYY-MM-DD') + 1 ");
            params.add(criteria.getEndDate().trim());
        }

        // Count Query
        String countSql = "SELECT COUNT(*) FROM inventory_audits ia JOIN assets a ON ia.asset_id = a.asset_id " + whereClause;
        int totalItems = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement countPs = conn.prepareStatement(countSql)) {

            for (int i = 0; i < params.size(); i++) {
                countPs.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = countPs.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getInt(1);
                }
            }
        }

        // Paginated Query
        String sortCol = "ia.audit_date";
        String sortOrder = "DESC".equalsIgnoreCase(criteria.getSortOrder()) ? "DESC" : "ASC";
        if ("asset_id".equalsIgnoreCase(criteria.getSortBy())) {
            sortCol = "ia.asset_id";
        } else if ("status".equalsIgnoreCase(criteria.getSortBy())) {
            sortCol = "ia.status";
        }

        String dataSql = "SELECT ia.audit_id, ia.asset_id, ia.audit_date, ia.verified_by, ia.status, ia.remarks, ia.created_at, " +
                "a.asset_name, a.category, a.department, a.location, a.status AS asset_status " +
                "FROM inventory_audits ia " +
                "JOIN assets a ON ia.asset_id = a.asset_id " +
                whereClause +
                " ORDER BY " + sortCol + " " + sortOrder + " " +
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<AuditRecord> list = new ArrayList<>();
        int offset = (criteria.getPage() - 1) * criteria.getSize();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement dataPs = conn.prepareStatement(dataSql)) {

            int idx = 1;
            for (Object param : params) {
                dataPs.setObject(idx++, param);
            }
            dataPs.setInt(idx++, offset);
            dataPs.setInt(idx, criteria.getSize());

            try (ResultSet rs = dataPs.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToAuditRecord(rs));
                }
            }
        }

        return new PagedResult<>(list, criteria.getPage(), criteria.getSize(), totalItems);
    }

    @Override
    public AuditSummary getSummary() throws SQLException {
        String sql = "SELECT COUNT(*) AS total, " +
                "COUNT(CASE WHEN status = 'VERIFIED' THEN 1 END) AS verified, " +
                "COUNT(CASE WHEN status = 'MISSING' THEN 1 END) AS missing, " +
                "COUNT(CASE WHEN status = 'MISLOCATED' THEN 1 END) AS mislocated " +
                "FROM inventory_audits";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return new AuditSummary(
                        rs.getInt("total"),
                        rs.getInt("verified"),
                        rs.getInt("missing"),
                        rs.getInt("mislocated")
                );
            }
        }
        return new AuditSummary(0, 0, 0, 0);
    }

    private AuditRecord mapRowToAuditRecord(ResultSet rs) throws SQLException {
        AuditRecord r = new AuditRecord();
        r.setAuditId(rs.getString("audit_id"));
        r.setAssetId(rs.getString("asset_id"));
        r.setAuditDate(rs.getTimestamp("audit_date"));
        r.setVerifiedBy(rs.getString("verified_by"));
        r.setStatus(rs.getString("status"));
        r.setRemarks(rs.getString("remarks"));
        r.setCreatedAt(rs.getTimestamp("created_at"));

        r.setAssetName(rs.getString("asset_name"));
        r.setCategory(rs.getString("category"));
        r.setDepartment(rs.getString("department"));
        r.setCurrentLocation(rs.getString("location"));
        r.setAssetStatus(rs.getString("asset_status"));
        return r;
    }
}
