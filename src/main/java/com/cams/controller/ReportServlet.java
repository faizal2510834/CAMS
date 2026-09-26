package com.cams.controller;

import com.cams.service.ReportService;
import com.cams.service.ReportServiceImpl;
import com.cams.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller servlet for Module 10: Reports Center (Admin-only).
 * Endpoints:
 * - GET /api/reports/summary                (Comprehensive analytical report)
 * - GET /api/reports/department-breakdown   (Department allocation metrics)
 * - GET /api/reports/export/csv             (Streamable CSV inventory export)
 */
@WebServlet(name = "ReportServlet", urlPatterns = {"/api/reports", "/api/reports/*"})
public class ReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ReportServlet.class.getName());

    private final ReportService reportService;

    public ReportServlet() {
        this(new ReportServiceImpl());
    }

    public ReportServlet(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || "/".equals(pathInfo) || "/summary".equalsIgnoreCase(pathInfo)) {
                String department = request.getParameter("department");
                String startDate = request.getParameter("startDate");
                String endDate = request.getParameter("endDate");

                Map<String, Object> report = reportService.getComprehensiveReport(department, startDate, endDate);
                JsonUtil.sendSuccess(response, "Comprehensive report generated successfully", report);

            } else if ("/department-breakdown".equalsIgnoreCase(pathInfo)) {
                List<Map<String, Object>> breakdown = reportService.getDepartmentBreakdown();
                JsonUtil.sendSuccess(response, "Department breakdown retrieved", breakdown);

            } else if ("/export/csv".equalsIgnoreCase(pathInfo)) {
                String department = request.getParameter("department");
                String startDate = request.getParameter("startDate");
                String endDate = request.getParameter("endDate");

                Map<String, Object> summary = reportService.getComprehensiveReport(department, startDate, endDate);
                List<Map<String, Object>> rows = reportService.getDepartmentBreakdown();
                String filename = "cams_institutional_report_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

                response.setContentType("text/csv");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

                try (PrintWriter writer = response.getWriter()) {
                    writer.println("# CAMS Institutional Asset & Operations Report");
                    writer.println("# Generated: " + LocalDate.now().toString());
                    writer.println();

                    writer.println("# Asset Status Summary");
                    writer.println("Total Assets,Available,Issued,Under Maintenance,Disposed");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> a = (Map<String, Object>) summary.get("assetSummary");
                    if (a != null) {
                        writer.printf("%s,%s,%s,%s,%s%n",
                                a.get("totalAssets"), a.get("availableAssets"), a.get("issuedAssets"),
                                a.get("underMaintenanceAssets"), a.get("disposedAssets"));
                    }
                    writer.println();

                    writer.println("# Department Breakdown");
                    writer.println("Department,Asset Count,Total Valuation (INR),Available,Issued,Under Maintenance,Disposed");
                    for (Map<String, Object> r : rows) {
                        writer.printf("\"%s\",%s,%.2f,%s,%s,%s,%s%n",
                                r.get("department"),
                                r.get("assetCount"),
                                (Double) r.get("totalAssetCost"),
                                r.get("availableCount"),
                                r.get("issuedCount"),
                                r.get("maintenanceCount"),
                                r.get("disposedCount"));
                    }
                }

            } else {
                JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown reports endpoint: " + pathInfo);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in ReportServlet", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }
}
