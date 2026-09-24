package com.cams.dao;

import com.cams.model.FurnitureDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/**
 * JDBC implementation of FurnitureDetailsDAO.
 * Reuses the caller's Connection for transaction consistency.
 */
public class FurnitureDetailsDAOImpl implements FurnitureDetailsDAO {

    @Override
    public boolean insertOrUpdate(Connection conn, String assetId, FurnitureDetails details) throws SQLException {
        if (assetId == null || details == null) {
            return false;
        }

        String updateSql = "UPDATE FURNITURE_DETAILS SET " +
                "FURNITURE_TYPE = ?, MATERIAL = ?, QUANTITY = ? " +
                "WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, details.getFurnitureType());
            ps.setString(2, details.getMaterial());
            if (details.getQuantity() != null) {
                ps.setInt(3, details.getQuantity());
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.setString(4, assetId.trim());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                return true;
            }
        }

        String insertSql = "INSERT INTO FURNITURE_DETAILS (" +
                "ASSET_ID, FURNITURE_TYPE, MATERIAL, QUANTITY" +
                ") VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, assetId.trim());
            ps.setString(2, details.getFurnitureType());
            ps.setString(3, details.getMaterial());
            if (details.getQuantity() != null) {
                ps.setInt(4, details.getQuantity());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<FurnitureDetails> findByAssetId(Connection conn, String assetId) throws SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT FURNITURE_TYPE, MATERIAL, QUANTITY " +
                "FROM FURNITURE_DETAILS WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FurnitureDetails details = new FurnitureDetails();
                    details.setFurnitureType(rs.getString("FURNITURE_TYPE"));
                    details.setMaterial(rs.getString("MATERIAL"));
                    int qty = rs.getInt("QUANTITY");
                    if (!rs.wasNull()) {
                        details.setQuantity(qty);
                    }
                    return Optional.of(details);
                }
            }
        }
        return Optional.empty();
    }
}
