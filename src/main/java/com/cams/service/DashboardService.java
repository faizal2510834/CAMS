package com.cams.service;

import java.sql.SQLException;
import java.util.Map;

/**
 * Service providing live aggregated metrics for role-based dashboards:
 * - Admin Dashboard: campus-wide KPIs, asset status counts, valuation, procurement, maintenance, overdue loans
 * - Faculty Dashboard: personal loans count, overdue count, active loan items list
 * - Technical Staff Dashboard: active repair queue, completed repairs, physical audit metrics, recent activity
 */
public interface DashboardService {

    Map<String, Object> getAdminDashboardData() throws SQLException;

    Map<String, Object> getFacultyDashboardData(Long userId) throws SQLException;

    Map<String, Object> getTechnicalDashboardData() throws SQLException;
}
