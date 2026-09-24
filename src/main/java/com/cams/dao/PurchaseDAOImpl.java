package com.cams.dao;

import com.cams.model.PagedResult;
import com.cams.model.Purchase;
import com.cams.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Implementation of PurchaseDAO for Oracle Database.
 */
public class PurchaseDAOImpl implements PurchaseDAO {

    private static final String SELECT_JOINED =
            "SELECT p.purchase_id, p.asset_id, p.vendor_id, p.invoice_number, p.purchase_date, p.cost, p.status, " +
            "       p.approved_by, p.approved_at, p.created_at, " +
            "       a.asset_name, v.vendor_name " +
            "FROM purchases p " +
            "LEFT JOIN assets a ON p.asset_id = a.asset_id " +
            "LEFT JOIN vendors v ON p.vendor_id = v.vendor_id ";

    @Override
    public Optional<Purchase> findById(String purchaseId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, purchaseId);
        }
    }

    @Override
    public Optional<Purchase> findById(Connection conn, String purchaseId) throws SQLException {
        String sql = SELECT_JOINED + " WHERE p.purchase_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPurchase(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Purchase> findByAssetId(String assetId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findByAssetId(conn, assetId);
        }
    }

    @Override
    public Optional<Purchase> findByAssetId(Connection conn, String assetId) throws SQLException {
        String sql = SELECT_JOINED + " WHERE p.asset_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPurchase(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByAssetId(String assetId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return existsByAssetId(conn, assetId);
        }
    }

    @Override
    public boolean existsByAssetId(Connection conn, String assetId) throws SQLException {
        String sql = "SELECT 1 FROM purchases WHERE asset_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean insert(Connection conn, Purchase purchase) throws SQLException {
        String sql = "INSERT INTO purchases (purchase_id, asset_id, vendor_id, invoice_number, purchase_date, cost, status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, purchase.getPurchaseId());
            ps.setString(2, purchase.getAssetId());
            ps.setString(3, purchase.getVendorId());
            ps.setString(4, purchase.getInvoiceNumber());
            ps.setDate(5, purchase.getPurchaseDate());
            ps.setBigDecimal(6, purchase.getCost());
            ps.setString(7, purchase.getStatus() != null ? purchase.getStatus() : "PENDING");
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateStatus(Connection conn, String purchaseId, String status, String approvedBy, Timestamp approvedAt) throws SQLException {
        String sql = "UPDATE purchases SET status = ?, approved_by = ?, approved_at = ? WHERE purchase_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, approvedBy);
            ps.setTimestamp(3, approvedAt);
            ps.setString(4, purchaseId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public PagedResult<Purchase> search(String status, String vendorId, int page, int size) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND p.status = ?");
            params.add(status.trim().toUpperCase());
        }

        if (vendorId != null && !vendorId.trim().isEmpty()) {
            where.append(" AND p.vendor_id = ?");
            params.add(vendorId.trim());
        }

        long totalItems = 0;
        String countSql = "SELECT COUNT(*) FROM purchases p" + where;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getLong(1);
                }
            }
        }

        int offset = (page - 1) * size;
        String dataSql = SELECT_JOINED + where + " ORDER BY p.created_at DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<Purchase> purchases = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(dataSql)) {
            int idx = 1;
            for (Object param : params) {
                ps.setObject(idx++, param);
            }
            ps.setInt(idx++, offset);
            ps.setInt(idx, size);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapRowToPurchase(rs));
                }
            }
        }

        return new PagedResult<>(purchases, page, size, totalItems);
    }

    private Purchase mapRowToPurchase(ResultSet rs) throws SQLException {
        Purchase p = new Purchase();
        p.setPurchaseId(rs.getString("purchase_id"));
        p.setAssetId(rs.getString("asset_id"));
        p.setVendorId(rs.getString("vendor_id"));
        p.setInvoiceNumber(rs.getString("invoice_number"));
        p.setPurchaseDate(rs.getDate("purchase_date"));
        p.setCost(rs.getBigDecimal("cost"));
        p.setStatus(rs.getString("status"));
        p.setApprovedBy(rs.getString("approved_by"));
        p.setApprovedAt(rs.getTimestamp("approved_at"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setAssetName(rs.getString("asset_name"));
        p.setVendorName(rs.getString("vendor_name"));
        return p;
    }
}
