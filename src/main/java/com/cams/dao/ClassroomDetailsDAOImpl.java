package com.cams.dao;

import com.cams.model.ClassroomDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/**
 * JDBC implementation of ClassroomDetailsDAO.
 * Reuses the caller's Connection for transaction consistency.
 */
public class ClassroomDetailsDAOImpl implements ClassroomDetailsDAO {

    @Override
    public boolean insertOrUpdate(Connection conn, String assetId, ClassroomDetails details) throws SQLException {
        if (assetId == null || details == null) {
            return false;
        }

        String updateSql = "UPDATE CLASSROOM_DETAILS SET " +
                "FURNITURE_DESC = ?, PROJECTOR = ?, SMART_BOARD = ?, AC = ?, SEATING_CAPACITY = ? " +
                "WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, details.getFurnitureDesc());
            ps.setString(2, details.getProjector());
            ps.setString(3, details.getSmartBoard());
            ps.setString(4, details.getAc());
            if (details.getSeatingCapacity() != null) {
                ps.setInt(5, details.getSeatingCapacity());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, assetId.trim());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                return true;
            }
        }

        String insertSql = "INSERT INTO CLASSROOM_DETAILS (" +
                "ASSET_ID, FURNITURE_DESC, PROJECTOR, SMART_BOARD, AC, SEATING_CAPACITY" +
                ") VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, assetId.trim());
            ps.setString(2, details.getFurnitureDesc());
            ps.setString(3, details.getProjector());
            ps.setString(4, details.getSmartBoard());
            ps.setString(5, details.getAc());
            if (details.getSeatingCapacity() != null) {
                ps.setInt(6, details.getSeatingCapacity());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<ClassroomDetails> findByAssetId(Connection conn, String assetId) throws SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT FURNITURE_DESC, PROJECTOR, SMART_BOARD, AC, SEATING_CAPACITY " +
                "FROM CLASSROOM_DETAILS WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ClassroomDetails details = new ClassroomDetails();
                    details.setFurnitureDesc(rs.getString("FURNITURE_DESC"));
                    details.setProjector(rs.getString("PROJECTOR"));
                    details.setSmartBoard(rs.getString("SMART_BOARD"));
                    details.setAc(rs.getString("AC"));
                    int cap = rs.getInt("SEATING_CAPACITY");
                    if (!rs.wasNull()) {
                        details.setSeatingCapacity(cap);
                    }
                    return Optional.of(details);
                }
            }
        }
        return Optional.empty();
    }
}
