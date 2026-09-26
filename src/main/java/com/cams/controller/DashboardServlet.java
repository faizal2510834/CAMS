package com.cams.controller;

import com.cams.service.DashboardService;
import com.cams.service.DashboardServiceImpl;
import com.cams.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller servlet for Module 10: Role-Based Live Dashboards.
 * Endpoints:
 * - GET /api/dashboard/admin      (Administrator only)
 * - GET /api/dashboard/faculty    (Faculty only)
 * - GET /api/dashboard/technical  (Technical Staff and Administrator)
 */
@WebServlet(name = "DashboardServlet", urlPatterns = {"/api/dashboard", "/api/dashboard/*"})
public class DashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DashboardServlet.class.getName());

    private final DashboardService dashboardService;

    public DashboardServlet() {
        this(new DashboardServiceImpl());
    }

    public DashboardServlet(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        HttpSession session = request.getSession(false);

        try {
            if ("/admin".equalsIgnoreCase(pathInfo)) {
                Map<String, Object> data = dashboardService.getAdminDashboardData();
                JsonUtil.sendSuccess(response, "Admin dashboard data loaded", data);

            } else if ("/faculty".equalsIgnoreCase(pathInfo)) {
                Long userId = session != null ? (Long) session.getAttribute("userId") : null;
                if (userId == null) {
                    JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "User session not found");
                    return;
                }
                Map<String, Object> data = dashboardService.getFacultyDashboardData(userId);
                JsonUtil.sendSuccess(response, "Faculty dashboard data loaded", data);

            } else if ("/technical".equalsIgnoreCase(pathInfo)) {
                Map<String, Object> data = dashboardService.getTechnicalDashboardData();
                JsonUtil.sendSuccess(response, "Technical dashboard data loaded", data);

            } else {
                JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown dashboard endpoint: " + pathInfo);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in DashboardServlet", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }
}
