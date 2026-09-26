package com.cams.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Service providing analytical aggregations for the Admin Reports Center:
 * - Asset status summary & percentages
 * - Category depreciation & book value
 * - Procurement & purchase history spend
 * - Circulation & overdue loan metrics
 * - Maintenance operational spend
 * - Inventory audit compliance (verified/missing/mislocated)
 * - Department-wise asset counts and allocation values
 */
public interface ReportService {

    Map<String, Object> getComprehensiveReport(String department, String startDate, String endDate) throws SQLException;

    List<Map<String, Object>> getDepartmentBreakdown() throws SQLException;
}
