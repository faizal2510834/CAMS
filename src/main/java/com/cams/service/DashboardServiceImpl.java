package com.cams.service;

import com.cams.model.DepreciationSummary;
import com.cams.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementation of DashboardService.
 * Reuses DepreciationService and executes live aggregation queries across existing tables.
 */
public class DashboardServiceImpl implements DashboardService {

    private static final Logger LOGGER = Logger.getLogger(DashboardServiceImpl.class.getName());

    private final DepreciationService depreciationService;

    public DashboardServiceImpl() {
        this(new DepreciationServiceImpl());
    }

    public DashboardServiceImpl(DepreciationService depreciationService) {
        this.depreciationService = depreciationService;
    }

    @Override
    public Map<String, Object> getAdminDashboardData() throws SQLException {
        Map<String, Object> data = new HashMap<>();

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Asset status counts
            String assetSql = "SELECT " +
                    "COUNT(*) AS TOTAL, " +
                    "NVL(SUM(CASE WHEN STATUS = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS AVAILABLE, " +
                    "NVL(SUM(CASE WHEN STATUS = 'ISSUED' THEN 1 ELSE 0 END), 0) AS ISSUED, " +
                    "NVL(SUM(CASE WHEN STATUS = 'UNDER_MAINTENANCE' THEN 1 ELSE 0 END), 0) AS UNDER_MAINTENANCE, " +
                    "NVL(SUM(CASE WHEN STATUS = 'DISPOSED' THEN 1 ELSE 0 END), 0) AS DISPOSED " +
                    "FROM ASSETS";
            try (PreparedStatement ps = conn.prepareStatement(assetSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> assetCounts = new HashMap<>();
                    assetCounts.put("total", rs.getLong("TOTAL"));
                    assetCounts.put("available", rs.getLong("AVAILABLE"));
                    assetCounts.put("issued", rs.getLong("ISSUED"));
                    assetCounts.put("underMaintenance", rs.getLong("UNDER_MAINTENANCE"));
                    assetCounts.put("disposed", rs.getLong("DISPOSED"));
                    data.put("assetCounts", assetCounts);
                }
            }

            // 2. Pending purchase approvals
            String purchSql = "SELECT COUNT(*) FROM PURCHASES WHERE STATUS = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(purchSql);
                 ResultSet rs = ps.executeQuery()) {
                long pendingPurchases = rs.next() ? rs.getLong(1) : 0L;
                data.put("pendingPurchasesCount", pendingPurchases);
            }

            // 3. Active maintenance tickets
            String maintSql = "SELECT COUNT(*) FROM MAINTENANCE WHERE STATUS IN ('SCHEDULED', 'IN_PROGRESS', 'REQUIRES_FURTHER_REPAIR')";
            try (PreparedStatement ps = conn.prepareStatement(maintSql);
                 ResultSet rs = ps.executeQuery()) {
                long activeMaintenance = rs.next() ? rs.getLong(1) : 0L;
                data.put("activeMaintenanceCount", activeMaintenance);
            }

            // 4. Overdue equipment loans
            String overdueSql = "SELECT COUNT(*) FROM ASSET_ISSUES WHERE STATUS = 'ISSUED' AND EXPECTED_RETURN_DATE < TRUNC(SYSDATE)";
            try (PreparedStatement ps = conn.prepareStatement(overdueSql);
                 ResultSet rs = ps.executeQuery()) {
                long overdueCount = rs.next() ? rs.getLong(1) : 0L;
                data.put("overdueLoansCount", overdueCount);
            }

            // 5. User accounts overview
            String userSql = "SELECT " +
                    "COUNT(*) AS TOTAL_USERS, " +
                    "NVL(SUM(CASE WHEN ACTIVE = 'Y' THEN 1 ELSE 0 END), 0) AS ACTIVE_USERS, " +
                    "NVL(SUM(CASE WHEN ROLE = 'Faculty' AND ACTIVE = 'Y' THEN 1 ELSE 0 END), 0) AS FACULTY_COUNT, " +
                    "NVL(SUM(CASE WHEN ROLE = 'Technical Staff' AND ACTIVE = 'Y' THEN 1 ELSE 0 END), 0) AS TECH_COUNT " +
                    "FROM USERS";
            try (PreparedStatement ps = conn.prepareStatement(userSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> userCounts = new HashMap<>();
                    userCounts.put("totalUsers", rs.getLong("TOTAL_USERS"));
                    userCounts.put("activeUsers", rs.getLong("ACTIVE_USERS"));
                    userCounts.put("facultyCount", rs.getLong("FACULTY_COUNT"));
                    userCounts.put("techCount", rs.getLong("TECH_COUNT"));
                    data.put("userCounts", userCounts);
                }
            }
        }

        // 6. Campus valuation & depreciation summary (Reuse Module 7)
        DepreciationSummary valuation = depreciationService.getDepreciationSummary();
        data.put("valuation", valuation);

        return data;
    }

    @Override
    public Map<String, Object> getFacultyDashboardData(Long userId) throws SQLException {
        Map<String, Object> data = new HashMap<>();
        if (userId == null) {
            return data;
        }

        try (Connection conn = DBConnection.getConnection()) {
            // 1. KPI Counts
            String kpiSql = "SELECT " +
                    "COUNT(*) AS TOTAL_LOANS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'ISSUED' THEN 1 ELSE 0 END), 0) AS CURRENTLY_ISSUED, " +
                    "NVL(SUM(CASE WHEN STATUS = 'ISSUED' AND EXPECTED_RETURN_DATE < TRUNC(SYSDATE) THEN 1 ELSE 0 END), 0) AS OVERDUE " +
                    "FROM ASSET_ISSUES " +
                    "WHERE ISSUED_TO_USER_ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(kpiSql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.put("totalLoans", rs.getLong("TOTAL_LOANS"));
                        data.put("currentlyIssuedCount", rs.getLong("CURRENTLY_ISSUED"));
                        data.put("overdueLoansCount", rs.getLong("OVERDUE"));
                    }
                }
            }

            // 2. Active loaned items list
            String itemsSql = "SELECT " +
                    "i.ISSUE_ID, i.ASSET_ID, a.ASSET_NAME, a.CATEGORY, a.LOCATION, " +
                    "i.ISSUE_DATE, i.EXPECTED_RETURN_DATE, " +
                    "CASE WHEN i.EXPECTED_RETURN_DATE < TRUNC(SYSDATE) THEN 1 ELSE 0 END AS IS_OVERDUE " +
                    "FROM ASSET_ISSUES i " +
                    "JOIN ASSETS a ON i.ASSET_ID = a.ASSET_ID " +
                    "WHERE i.ISSUED_TO_USER_ID = ? AND i.STATUS = 'ISSUED' " +
                    "ORDER BY i.EXPECTED_RETURN_DATE ASC";

            List<Map<String, Object>> activeLoans = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("issueId", rs.getString("ISSUE_ID"));
                        item.put("assetId", rs.getString("ASSET_ID"));
                        item.put("assetName", rs.getString("ASSET_NAME"));
                        item.put("category", rs.getString("CATEGORY"));
                        item.put("location", rs.getString("LOCATION"));
                        item.put("issueDate", rs.getDate("ISSUE_DATE"));
                        item.put("expectedReturnDate", rs.getDate("EXPECTED_RETURN_DATE"));
                        item.put("isOverdue", rs.getInt("IS_OVERDUE") == 1);
                        activeLoans.add(item);
                    }
                }
            }
            data.put("activeLoans", activeLoans);
        }

        return data;
    }

    @Override
    public Map<String, Object> getTechnicalDashboardData() throws SQLException {
        Map<String, Object> data = new HashMap<>();

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Maintenance counts
            String maintSql = "SELECT " +
                    "COUNT(*) AS TOTAL_TICKETS, " +
                    "NVL(SUM(CASE WHEN STATUS IN ('SCHEDULED', 'IN_PROGRESS', 'REQUIRES_FURTHER_REPAIR') THEN 1 ELSE 0 END), 0) AS ACTIVE_QUEUE, " +
                    "NVL(SUM(CASE WHEN STATUS = 'SCHEDULED' THEN 1 ELSE 0 END), 0) AS SCHEDULED, " +
                    "NVL(SUM(CASE WHEN STATUS = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0) AS IN_PROGRESS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS COMPLETED, " +
                    "NVL(SUM(CASE WHEN STATUS = 'COMPLETED' THEN COST ELSE 0 END), 0) AS TOTAL_COST " +
                    "FROM MAINTENANCE";
            try (PreparedStatement ps = conn.prepareStatement(maintSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> maint = new HashMap<>();
                    maint.put("totalTickets", rs.getLong("TOTAL_TICKETS"));
                    maint.put("activeQueue", rs.getLong("ACTIVE_QUEUE"));
                    maint.put("scheduled", rs.getLong("SCHEDULED"));
                    maint.put("inProgress", rs.getLong("IN_PROGRESS"));
                    maint.put("completed", rs.getLong("COMPLETED"));
                    maint.put("totalCost", rs.getDouble("TOTAL_COST"));
                    data.put("maintenance", maint);
                }
            }

            // 2. Physical Audits summary (Table INVENTORY_AUDITS, column STATUS)
            String auditSql = "SELECT " +
                    "COUNT(*) AS TOTAL_AUDITS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'VERIFIED' THEN 1 ELSE 0 END), 0) AS VERIFIED, " +
                    "NVL(SUM(CASE WHEN STATUS = 'MISSING' THEN 1 ELSE 0 END), 0) AS MISSING, " +
                    "NVL(SUM(CASE WHEN STATUS = 'MISLOCATED' THEN 1 ELSE 0 END), 0) AS MISLOCATED " +
                    "FROM INVENTORY_AUDITS";
            try (PreparedStatement ps = conn.prepareStatement(auditSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> audit = new HashMap<>();
                    audit.put("totalAudits", rs.getLong("TOTAL_AUDITS"));
                    audit.put("verified", rs.getLong("VERIFIED"));
                    audit.put("missing", rs.getLong("MISSING"));
                    audit.put("mislocated", rs.getLong("MISLOCATED"));
                    data.put("audit", audit);
                }
            }

            // 3. Recent 5 Maintenance Tickets
            String recentMaintSql = "SELECT m.MAINTENANCE_ID, m.ASSET_ID, a.ASSET_NAME, m.SCHEDULED_DATE, m.FAULT_DESCRIPTION, m.STATUS, m.COST, m.TECHNICIAN " +
                    "FROM MAINTENANCE m " +
                    "JOIN ASSETS a ON m.ASSET_ID = a.ASSET_ID " +
                    "ORDER BY m.CREATED_AT DESC " +
                    "FETCH FIRST 5 ROWS ONLY";
            List<Map<String, Object>> recentMaint = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(recentMaintSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("maintenanceId", rs.getString("MAINTENANCE_ID"));
                    row.put("assetId", rs.getString("ASSET_ID"));
                    row.put("assetName", rs.getString("ASSET_NAME"));
                    row.put("scheduledDate", rs.getDate("SCHEDULED_DATE"));
                    row.put("faultDescription", rs.getString("FAULT_DESCRIPTION"));
                    row.put("status", rs.getString("STATUS"));
                    row.put("cost", rs.getDouble("COST"));
                    row.put("technician", rs.getString("TECHNICIAN"));
                    recentMaint.add(row);
                }
            }
            data.put("recentMaintenance", recentMaint);

            // 4. Recent 5 Audit Scans
            String recentAuditSql = "SELECT ia.AUDIT_ID, ia.ASSET_ID, a.ASSET_NAME, ia.AUDIT_DATE, ia.VERIFIED_BY, ia.STATUS, ia.REMARKS " +
                    "FROM INVENTORY_AUDITS ia " +
                    "JOIN ASSETS a ON ia.ASSET_ID = a.ASSET_ID " +
                    "ORDER BY ia.AUDIT_DATE DESC " +
                    "FETCH FIRST 5 ROWS ONLY";
            List<Map<String, Object>> recentAudits = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(recentAuditSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("auditId", rs.getString("AUDIT_ID"));
                    row.put("assetId", rs.getString("ASSET_ID"));
                    row.put("assetName", rs.getString("ASSET_NAME"));
                    row.put("auditDate", rs.getTimestamp("AUDIT_DATE"));
                    row.put("verifiedBy", rs.getString("VERIFIED_BY"));
                    row.put("status", rs.getString("STATUS"));
                    row.put("remarks", rs.getString("REMARKS"));
                    recentAudits.add(row);
                }
            }
            data.put("recentAudits", recentAudits);
        }

        return data;
    }
}
