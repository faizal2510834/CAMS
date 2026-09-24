package com.cams.dao;

import com.cams.model.LabDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC implementation of LabDetailsDAO.
 * Reuses the caller's Connection for transaction consistency.
 */
public class LabDetailsDAOImpl implements LabDetailsDAO {

    @Override
    public boolean insertOrUpdate(Connection conn, String assetId, LabDetails details) throws SQLException {
        if (assetId == null || details == null) {
            return false;
        }

        String updateSql = "UPDATE LAB_DETAILS SET " +
                "EQUIPMENT_TYPE = ?, EQUIPMENT_CONDITION = ?, LAST_CALIBRATION_DATE = ? " +
                "WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, details.getEquipmentType());
            ps.setString(2, details.getCondition());
            ps.setDate(3, details.getLastCalibrationDate());
            ps.setString(4, assetId.trim());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                return true;
            }
        }

        String insertSql = "INSERT INTO LAB_DETAILS (" +
                "ASSET_ID, EQUIPMENT_TYPE, EQUIPMENT_CONDITION, LAST_CALIBRATION_DATE" +
                ") VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, assetId.trim());
            ps.setString(2, details.getEquipmentType());
            ps.setString(3, details.getCondition());
            ps.setDate(4, details.getLastCalibrationDate());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<LabDetails> findByAssetId(Connection conn, String assetId) throws SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT EQUIPMENT_TYPE, EQUIPMENT_CONDITION, LAST_CALIBRATION_DATE " +
                "FROM LAB_DETAILS WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LabDetails details = new LabDetails();
                    details.setEquipmentType(rs.getString("EQUIPMENT_TYPE"));
                    details.setCondition(rs.getString("EQUIPMENT_CONDITION"));
                    details.setLastCalibrationDate(rs.getDate("LAST_CALIBRATION_DATE"));
                    return Optional.of(details);
                }
            }
        }
        return Optional.empty();
    }
}
