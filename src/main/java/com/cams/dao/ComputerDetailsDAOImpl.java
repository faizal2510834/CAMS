package com.cams.dao;

import com.cams.model.ComputerDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC implementation of ComputerDetailsDAO.
 * Reuses the caller's Connection for transaction consistency.
 */
public class ComputerDetailsDAOImpl implements ComputerDetailsDAO {

    @Override
    public boolean insertOrUpdate(Connection conn, String assetId, ComputerDetails details) throws SQLException {
        if (assetId == null || details == null) {
            return false;
        }

        String updateSql = "UPDATE COMPUTER_DETAILS SET " +
                "CPU = ?, MONITOR = ?, KEYBOARD = ?, MOUSE = ?, PRINTER = ?, SOFTWARE = ?, IP_ADDRESS = ? " +
                "WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, details.getCpu());
            ps.setString(2, details.getMonitor());
            ps.setString(3, details.getKeyboard());
            ps.setString(4, details.getMouse());
            ps.setString(5, details.getPrinter());
            ps.setString(6, details.getSoftware());
            ps.setString(7, details.getIpAddress());
            ps.setString(8, assetId.trim());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                return true;
            }
        }

        String insertSql = "INSERT INTO COMPUTER_DETAILS (" +
                "ASSET_ID, CPU, MONITOR, KEYBOARD, MOUSE, PRINTER, SOFTWARE, IP_ADDRESS" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, assetId.trim());
            ps.setString(2, details.getCpu());
            ps.setString(3, details.getMonitor());
            ps.setString(4, details.getKeyboard());
            ps.setString(5, details.getMouse());
            ps.setString(6, details.getPrinter());
            ps.setString(7, details.getSoftware());
            ps.setString(8, details.getIpAddress());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<ComputerDetails> findByAssetId(Connection conn, String assetId) throws SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT CPU, MONITOR, KEYBOARD, MOUSE, PRINTER, SOFTWARE, IP_ADDRESS " +
                "FROM COMPUTER_DETAILS WHERE ASSET_ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ComputerDetails details = new ComputerDetails();
                    details.setCpu(rs.getString("CPU"));
                    details.setMonitor(rs.getString("MONITOR"));
                    details.setKeyboard(rs.getString("KEYBOARD"));
                    details.setMouse(rs.getString("MOUSE"));
                    details.setPrinter(rs.getString("PRINTER"));
                    details.setSoftware(rs.getString("SOFTWARE"));
                    details.setIpAddress(rs.getString("IP_ADDRESS"));
                    return Optional.of(details);
                }
            }
        }
        return Optional.empty();
    }
}
