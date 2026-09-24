package com.cams.dao;

import com.cams.model.PagedResult;
import com.cams.model.Vendor;
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
 * JDBC Implementation of VendorDAO for Oracle Database.
 */
public class VendorDAOImpl implements VendorDAO {

    private static final String SELECT_COLUMNS = "vendor_id, vendor_name, contact, email, address, active, created_at, updated_at";

    @Override
    public Optional<Vendor> findById(String vendorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, vendorId);
        }
    }

    @Override
    public Optional<Vendor> findById(Connection conn, String vendorId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM vendors WHERE vendor_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToVendor(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(String vendorId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return existsById(conn, vendorId);
        }
    }

    @Override
    public boolean existsById(Connection conn, String vendorId) throws SQLException {
        String sql = "SELECT 1 FROM vendors WHERE vendor_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean insert(Connection conn, Vendor vendor) throws SQLException {
        String sql = "INSERT INTO vendors (vendor_id, vendor_name, contact, email, address, active, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendor.getVendorId());
            ps.setString(2, vendor.getVendorName());
            ps.setString(3, vendor.getContact());
            ps.setString(4, vendor.getEmail());
            ps.setString(5, vendor.getAddress());
            ps.setString(6, vendor.getActive() != null ? vendor.getActive() : "Y");
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean update(Connection conn, Vendor vendor) throws SQLException {
        String sql = "UPDATE vendors SET vendor_name = ?, contact = ?, email = ?, address = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE vendor_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendor.getVendorName());
            ps.setString(2, vendor.getContact());
            ps.setString(3, vendor.getEmail());
            ps.setString(4, vendor.getAddress());
            ps.setString(5, vendor.getVendorId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deactivate(Connection conn, String vendorId) throws SQLException {
        String sql = "UPDATE vendors SET active = 'N', updated_at = CURRENT_TIMESTAMP WHERE vendor_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vendorId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public PagedResult<Vendor> search(String search, String active, int page, int size) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND (LOWER(vendor_name) LIKE ? OR LOWER(vendor_id) LIKE ? OR LOWER(contact) LIKE ? OR LOWER(email) LIKE ?)");
            String pattern = "%" + search.trim().toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        if (active != null && !active.trim().isEmpty()) {
            where.append(" AND active = ?");
            params.add(active.trim().toUpperCase());
        }

        long totalItems = 0;
        String countSql = "SELECT COUNT(*) FROM vendors" + where;
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
        String dataSql = "SELECT " + SELECT_COLUMNS + " FROM vendors" + where +
                         " ORDER BY vendor_name ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<Vendor> vendors = new ArrayList<>();
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
                    vendors.add(mapRowToVendor(rs));
                }
            }
        }

        return new PagedResult<>(vendors, page, size, totalItems);
    }

    @Override
    public List<Vendor> findActiveVendors() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM vendors WHERE active = 'Y' ORDER BY vendor_name ASC";
        List<Vendor> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToVendor(rs));
            }
        }
        return list;
    }

    private Vendor mapRowToVendor(ResultSet rs) throws SQLException {
        Vendor v = new Vendor();
        v.setVendorId(rs.getString("vendor_id"));
        v.setVendorName(rs.getString("vendor_name"));
        v.setContact(rs.getString("contact"));
        v.setEmail(rs.getString("email"));
        v.setAddress(rs.getString("address"));
        v.setActive(rs.getString("active"));
        v.setCreatedAt(rs.getTimestamp("created_at"));
        v.setUpdatedAt(rs.getTimestamp("updated_at"));
        return v;
    }
}
