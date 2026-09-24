package com.cams.dao;

import com.cams.model.Asset;
import com.cams.model.AssetQueryCriteria;
import com.cams.model.PagedResult;
import com.cams.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * JDBC Implementation of AssetDAO using Oracle Database 19c/21c/23c syntax.
 * Strictly uses PreparedStatement parameters for all filters and whitelisted sort clauses.
 */
public class AssetDAOImpl implements AssetDAO {

    private static final Logger LOGGER = Logger.getLogger(AssetDAOImpl.class.getName());

    private static final String SELECT_COLUMNS =
            "ASSET_ID, ASSET_NAME, CATEGORY, DEPARTMENT, PURCHASE_DATE, PURCHASE_COST, " +
            "VENDOR_ID, WARRANTY_EXPIRY, STATUS, LOCATION, DISPOSED_DATE, DISPOSAL_REASON, " +
            "CREATED_AT, UPDATED_AT";

    @Override
    public Optional<Asset> findById(String assetId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, assetId);
        }
    }

    @Override
    public Optional<Asset> findById(Connection conn, String assetId) throws SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM ASSETS WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToAsset(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean create(Asset asset) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return create(conn, asset);
        }
    }

    @Override
    public boolean create(Connection conn, Asset asset) throws SQLException {
        String sql = "INSERT INTO ASSETS (" +
                "ASSET_ID, ASSET_NAME, CATEGORY, DEPARTMENT, PURCHASE_DATE, PURCHASE_COST, " +
                "VENDOR_ID, WARRANTY_EXPIRY, STATUS, LOCATION, CREATED_AT, UPDATED_AT" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, asset.getAssetId().trim());
            ps.setString(2, asset.getAssetName().trim());
            ps.setString(3, asset.getCategory().trim());
            ps.setString(4, asset.getDepartment().trim());
            ps.setDate(5, asset.getPurchaseDate());
            ps.setBigDecimal(6, asset.getPurchaseCost());
            ps.setString(7, asset.getVendorId());
            ps.setDate(8, asset.getWarrantyExpiry());
            ps.setString(9, asset.getStatus() != null ? asset.getStatus().trim() : "AVAILABLE");
            ps.setString(10, asset.getLocation().trim());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean update(Asset asset) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, asset);
        }
    }

    @Override
    public boolean update(Connection conn, Asset asset) throws SQLException {
        // ASSET_ID and STATUS are immutable via standard update
        String sql = "UPDATE ASSETS SET " +
                "ASSET_NAME = ?, CATEGORY = ?, DEPARTMENT = ?, PURCHASE_DATE = ?, " +
                "PURCHASE_COST = ?, VENDOR_ID = ?, WARRANTY_EXPIRY = ?, LOCATION = ?, " +
                "UPDATED_AT = CURRENT_TIMESTAMP " +
                "WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, asset.getAssetName().trim());
            ps.setString(2, asset.getCategory().trim());
            ps.setString(3, asset.getDepartment().trim());
            ps.setDate(4, asset.getPurchaseDate());
            ps.setBigDecimal(5, asset.getPurchaseCost());
            ps.setString(6, asset.getVendorId());
            ps.setDate(7, asset.getWarrantyExpiry());
            ps.setString(8, asset.getLocation().trim());
            ps.setString(9, asset.getAssetId().trim());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean retire(String assetId, Date disposedDate, String reason) throws SQLException {
        String sql = "UPDATE ASSETS SET " +
                "STATUS = 'DISPOSED', DISPOSED_DATE = ?, DISPOSAL_REASON = ?, " +
                "UPDATED_AT = CURRENT_TIMESTAMP " +
                "WHERE ASSET_ID = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, disposedDate);
            ps.setString(2, reason != null ? reason.trim() : "Retired from operational inventory");
            ps.setString(3, assetId.trim());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int changeStatus(Connection con, String assetId, String expectedStatus, String newStatus) throws SQLException {
        String sql = "UPDATE ASSETS SET STATUS = ?, UPDATED_AT = CURRENT_TIMESTAMP " +
                "WHERE ASSET_ID = ? AND STATUS = ?";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newStatus.trim());
            ps.setString(2, assetId.trim());
            ps.setString(3, expectedStatus.trim());

            return ps.executeUpdate();
        }
    }

    @Override
    public PagedResult<Asset> search(AssetQueryCriteria criteria) throws SQLException {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (!criteria.isIncludeDisposed()) {
            whereClause.append(" AND STATUS != 'DISPOSED' ");
        }

        if (criteria.getKeyword() != null) {
            whereClause.append(" AND (LOWER(ASSET_ID) LIKE ? OR LOWER(ASSET_NAME) LIKE ?) ");
            String kw = "%" + criteria.getKeyword().toLowerCase() + "%";
            params.add(kw);
            params.add(kw);
        }

        if (criteria.getCategory() != null) {
            whereClause.append(" AND CATEGORY = ? ");
            params.add(criteria.getCategory());
        }

        if (criteria.getDepartment() != null) {
            whereClause.append(" AND DEPARTMENT = ? ");
            params.add(criteria.getDepartment());
        }

        if (criteria.getStatus() != null) {
            whereClause.append(" AND STATUS = ? ");
            params.add(criteria.getStatus());
        }

        if (criteria.getLocation() != null) {
            whereClause.append(" AND LOCATION = ? ");
            params.add(criteria.getLocation());
        }

        // 1. Count query
        String countSql = "SELECT COUNT(*) FROM ASSETS" + whereClause;
        long totalItems = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement countPs = conn.prepareStatement(countSql)) {

            for (int i = 0; i < params.size(); i++) {
                countPs.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = countPs.executeQuery()) {
                if (rs.next()) {
                    totalItems = rs.getLong(1);
                }
            }
        }

        // 2. Data query with OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        String dataSql = "SELECT " + SELECT_COLUMNS + " FROM ASSETS" + whereClause +
                " ORDER BY " + criteria.getSortBy() + " " + criteria.getSortOrder() +
                " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<Asset> assets = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement dataPs = conn.prepareStatement(dataSql)) {

            int paramIndex = 1;
            for (Object param : params) {
                dataPs.setObject(paramIndex++, param);
            }
            dataPs.setInt(paramIndex++, criteria.getOffset());
            dataPs.setInt(paramIndex, criteria.getSize());

            try (ResultSet rs = dataPs.executeQuery()) {
                while (rs.next()) {
                    assets.add(mapRowToAsset(rs));
                }
            }
        }

        return new PagedResult<>(assets, criteria.getPage(), criteria.getSize(), totalItems);
    }

    private Asset mapRowToAsset(ResultSet rs) throws SQLException {
        Asset asset = new Asset();
        asset.setAssetId(rs.getString("ASSET_ID"));
        asset.setAssetName(rs.getString("ASSET_NAME"));
        asset.setCategory(rs.getString("CATEGORY"));
        asset.setDepartment(rs.getString("DEPARTMENT"));
        asset.setPurchaseDate(rs.getDate("PURCHASE_DATE"));
        asset.setPurchaseCost(rs.getBigDecimal("PURCHASE_COST"));
        asset.setVendorId(rs.getString("VENDOR_ID"));
        asset.setWarrantyExpiry(rs.getDate("WARRANTY_EXPIRY"));
        asset.setStatus(rs.getString("STATUS"));
        asset.setLocation(rs.getString("LOCATION"));
        asset.setDisposedDate(rs.getDate("DISPOSED_DATE"));
        asset.setDisposalReason(rs.getString("DISPOSAL_REASON"));
        asset.setCreatedAt(rs.getTimestamp("CREATED_AT"));
        asset.setUpdatedAt(rs.getTimestamp("UPDATED_AT"));
        return asset;
    }
}
