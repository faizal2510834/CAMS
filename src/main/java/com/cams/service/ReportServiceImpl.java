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
 * Implementation of ReportService for Module 10: Reports Center.
 */
public class ReportServiceImpl implements ReportService {

    private static final Logger LOGGER = Logger.getLogger(ReportServiceImpl.class.getName());

    private final DepreciationService depreciationService;

    public ReportServiceImpl() {
        this(new DepreciationServiceImpl());
    }

    public ReportServiceImpl(DepreciationService depreciationService) {
        this.depreciationService = depreciationService;
    }

    @Override
    public Map<String, Object> getComprehensiveReport(String department, String startDate, String endDate) throws SQLException {
        Map<String, Object> report = new HashMap<>();
        boolean filterDept = department != null && !department.trim().isEmpty() && !"ALL".equalsIgnoreCase(department.trim());

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Asset Inventory Summary
            StringBuilder assetSql = new StringBuilder("SELECT " +
                    "COUNT(*) AS TOTAL, " +
                    "NVL(SUM(CASE WHEN STATUS = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS AVAILABLE, " +
                    "NVL(SUM(CASE WHEN STATUS = 'ISSUED' THEN 1 ELSE 0 END), 0) AS ISSUED, " +
                    "NVL(SUM(CASE WHEN STATUS = 'UNDER_MAINTENANCE' THEN 1 ELSE 0 END), 0) AS UNDER_MAINTENANCE, " +
                    "NVL(SUM(CASE WHEN STATUS = 'DISPOSED' THEN 1 ELSE 0 END), 0) AS DISPOSED, " +
                    "NVL(SUM(PURCHASE_COST), 0) AS TOTAL_PURCHASE_COST " +
                    "FROM ASSETS WHERE 1=1 ");
            if (filterDept) {
                assetSql.append("AND DEPARTMENT = ? ");
            }

            try (PreparedStatement ps = conn.prepareStatement(assetSql.toString())) {
                if (filterDept) {
                    ps.setString(1, department.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> assetSummary = new HashMap<>();
                        long total = rs.getLong("TOTAL");
                        long available = rs.getLong("AVAILABLE");
                        long issued = rs.getLong("ISSUED");
                        long maint = rs.getLong("UNDER_MAINTENANCE");
                        long disposed = rs.getLong("DISPOSED");

                        assetSummary.put("totalAssets", total);
                        assetSummary.put("availableAssets", available);
                        assetSummary.put("issuedAssets", issued);
                        assetSummary.put("underMaintenanceAssets", maint);
                        assetSummary.put("disposedAssets", disposed);
                        assetSummary.put("totalPurchaseCost", rs.getDouble("TOTAL_PURCHASE_COST"));

                        // Calculate percentages
                        if (total > 0) {
                            assetSummary.put("availablePct", Math.round((double) available / total * 1000.0) / 10.0);
                            assetSummary.put("issuedPct", Math.round((double) issued / total * 1000.0) / 10.0);
                            assetSummary.put("maintenancePct", Math.round((double) maint / total * 1000.0) / 10.0);
                            assetSummary.put("disposedPct", Math.round((double) disposed / total * 1000.0) / 10.0);
                        } else {
                            assetSummary.put("availablePct", 0.0);
                            assetSummary.put("issuedPct", 0.0);
                            assetSummary.put("maintenancePct", 0.0);
                            assetSummary.put("disposedPct", 0.0);
                        }
                        report.put("assetSummary", assetSummary);
                        report.put("assetStatusSummary", assetSummary);
                    }
                }
            }

            // 2. Purchases & Procurement Summary
            StringBuilder purchSql = new StringBuilder("SELECT " +
                    "COUNT(*) AS TOTAL_ORDERS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'PENDING' THEN 1 ELSE 0 END), 0) AS PENDING_COUNT, " +
                    "NVL(SUM(CASE WHEN STATUS = 'APPROVED' THEN 1 ELSE 0 END), 0) AS APPROVED_COUNT, " +
                    "NVL(SUM(CASE WHEN STATUS = 'REJECTED' THEN 1 ELSE 0 END), 0) AS REJECTED_COUNT, " +
                    "NVL(SUM(CASE WHEN STATUS = 'APPROVED' THEN COST ELSE 0 END), 0) AS APPROVED_SPEND, " +
                    "NVL(SUM(COST), 0) AS TOTAL_ORDER_VALUE " +
                    "FROM PURCHASES p WHERE 1=1 ");

            if (filterDept) {
                purchSql.append("AND EXISTS (SELECT 1 FROM ASSETS a WHERE a.ASSET_ID = p.ASSET_ID AND a.DEPARTMENT = ?) ");
            }

            try (PreparedStatement ps = conn.prepareStatement(purchSql.toString())) {
                if (filterDept) {
                    ps.setString(1, department.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> purch = new HashMap<>();
                        purch.put("totalOrders", rs.getLong("TOTAL_ORDERS"));
                        purch.put("totalPurchases", rs.getLong("TOTAL_ORDERS"));
                        purch.put("pendingCount", rs.getLong("PENDING_COUNT"));
                        purch.put("approvedCount", rs.getLong("APPROVED_COUNT"));
                        purch.put("rejectedCount", rs.getLong("REJECTED_COUNT"));
                        purch.put("approvedSpend", rs.getDouble("APPROVED_SPEND"));
                        purch.put("totalPurchaseCost", rs.getDouble("APPROVED_SPEND"));
                        purch.put("totalOrderValue", rs.getDouble("TOTAL_ORDER_VALUE"));
                        report.put("procurementSummary", purch);
                        report.put("purchaseSummary", purch);
                    }
                }
            }

            // 3. Circulation & Loans Summary
            StringBuilder issueSql = new StringBuilder("SELECT " +
                    "COUNT(*) AS TOTAL_ISSUES, " +
                    "NVL(SUM(CASE WHEN STATUS = 'ISSUED' THEN 1 ELSE 0 END), 0) AS ACTIVE_LOANS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'ISSUED' AND EXPECTED_RETURN_DATE < TRUNC(SYSDATE) THEN 1 ELSE 0 END), 0) AS OVERDUE_LOANS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'RETURNED' THEN 1 ELSE 0 END), 0) AS RETURNED_COUNT, " +
                    "NVL(SUM(CASE WHEN CONDITION_ON_RETURN = 'DAMAGED' THEN 1 ELSE 0 END), 0) AS DAMAGED_RETURNS " +
                    "FROM ASSET_ISSUES WHERE 1=1 ");

            if (filterDept) {
                issueSql.append("AND ISSUED_TO_DEPARTMENT = ? ");
            }

            try (PreparedStatement ps = conn.prepareStatement(issueSql.toString())) {
                if (filterDept) {
                    ps.setString(1, department.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> issueData = new HashMap<>();
                        issueData.put("totalIssues", rs.getLong("TOTAL_ISSUES"));
                        issueData.put("activeLoans", rs.getLong("ACTIVE_LOANS"));
                        issueData.put("overdueLoans", rs.getLong("OVERDUE_LOANS"));
                        issueData.put("returnedCount", rs.getLong("RETURNED_COUNT"));
                        issueData.put("damagedReturns", rs.getLong("DAMAGED_RETURNS"));
                        report.put("circulationSummary", issueData);
                    }
                }
            }

            // 4. Maintenance Summary
            StringBuilder maintSql = new StringBuilder("SELECT " +
                    "COUNT(*) AS TOTAL_TICKETS, " +
                    "NVL(SUM(CASE WHEN STATUS IN ('SCHEDULED', 'IN_PROGRESS', 'REQUIRES_FURTHER_REPAIR') THEN 1 ELSE 0 END), 0) AS OPEN_QUEUE, " +
                    "NVL(SUM(CASE WHEN STATUS = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS COMPLETED_COUNT, " +
                    "NVL(SUM(CASE WHEN STATUS = 'COMPLETED' THEN COST ELSE 0 END), 0) AS TOTAL_MAINT_COST " +
                    "FROM MAINTENANCE m WHERE 1=1 ");

            if (filterDept) {
                maintSql.append("AND EXISTS (SELECT 1 FROM ASSETS a WHERE a.ASSET_ID = m.ASSET_ID AND a.DEPARTMENT = ?) ");
            }

            try (PreparedStatement ps = conn.prepareStatement(maintSql.toString())) {
                if (filterDept) {
                    ps.setString(1, department.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> maint = new HashMap<>();
                        maint.put("totalTickets", rs.getLong("TOTAL_TICKETS"));
                        maint.put("openQueue", rs.getLong("OPEN_QUEUE"));
                        maint.put("completedCount", rs.getLong("COMPLETED_COUNT"));
                        maint.put("totalMaintenanceCost", rs.getDouble("TOTAL_MAINT_COST"));
                        report.put("maintenanceSummary", maint);
                    }
                }
            }

            // 5. Physical Inventory Audit Summary
            StringBuilder auditSql = new StringBuilder("SELECT " +
                    "COUNT(*) AS TOTAL_AUDITS, " +
                    "NVL(SUM(CASE WHEN STATUS = 'VERIFIED' THEN 1 ELSE 0 END), 0) AS VERIFIED_COUNT, " +
                    "NVL(SUM(CASE WHEN STATUS = 'MISSING' THEN 1 ELSE 0 END), 0) AS MISSING_COUNT, " +
                    "NVL(SUM(CASE WHEN STATUS = 'MISLOCATED' THEN 1 ELSE 0 END), 0) AS MISLOCATED_COUNT " +
                    "FROM INVENTORY_AUDITS ia WHERE 1=1 ");

            if (filterDept) {
                auditSql.append("AND EXISTS (SELECT 1 FROM ASSETS a WHERE a.ASSET_ID = ia.ASSET_ID AND a.DEPARTMENT = ?) ");
            }

            try (PreparedStatement ps = conn.prepareStatement(auditSql.toString())) {
                if (filterDept) {
                    ps.setString(1, department.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> audit = new HashMap<>();
                        long totalAudits = rs.getLong("TOTAL_AUDITS");
                        audit.put("totalAudits", totalAudits);
                        audit.put("verifiedCount", rs.getLong("VERIFIED_COUNT"));
                        audit.put("missingCount", rs.getLong("MISSING_COUNT"));
                        audit.put("mislocatedCount", rs.getLong("MISLOCATED_COUNT"));
                        report.put("auditSummary", audit);
                    }
                }
            }
        }

        // 6. Valuation Summary (Module 7)
        DepreciationSummary valuation = depreciationService.getDepreciationSummary();
        report.put("valuationSummary", valuation);

        // 7. Department Breakdown Table
        report.put("departmentBreakdown", getDepartmentBreakdown());

        return report;
    }

    @Override
    public List<Map<String, Object>> getDepartmentBreakdown() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT " +
                "d.DEPARTMENT_NAME, " +
                "COUNT(a.ASSET_ID) AS ASSET_COUNT, " +
                "NVL(SUM(a.PURCHASE_COST), 0) AS TOTAL_ASSET_COST, " +
                "NVL(SUM(CASE WHEN a.STATUS = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS AVAILABLE_COUNT, " +
                "NVL(SUM(CASE WHEN a.STATUS = 'ISSUED' THEN 1 ELSE 0 END), 0) AS ISSUED_COUNT, " +
                "NVL(SUM(CASE WHEN a.STATUS = 'UNDER_MAINTENANCE' THEN 1 ELSE 0 END), 0) AS MAINTENANCE_COUNT, " +
                "NVL(SUM(CASE WHEN a.STATUS = 'DISPOSED' THEN 1 ELSE 0 END), 0) AS DISPOSED_COUNT " +
                "FROM DEPARTMENTS d " +
                "LEFT JOIN ASSETS a ON d.DEPARTMENT_NAME = a.DEPARTMENT " +
                "GROUP BY d.DEPARTMENT_NAME " +
                "ORDER BY d.DEPARTMENT_NAME ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("department", rs.getString("DEPARTMENT_NAME"));
                row.put("assetCount", rs.getLong("ASSET_COUNT"));
                row.put("totalAssetCost", rs.getDouble("TOTAL_ASSET_COST"));
                row.put("availableCount", rs.getLong("AVAILABLE_COUNT"));
                row.put("issuedCount", rs.getLong("ISSUED_COUNT"));
                row.put("maintenanceCount", rs.getLong("MAINTENANCE_COUNT"));
                row.put("disposedCount", rs.getLong("DISPOSED_COUNT"));
                list.add(row);
            }
        }

        return list;
    }
}
