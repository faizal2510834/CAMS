package com.cams.controller;

import com.cams.model.PingResult;
import com.cams.service.PingService;
import com.cams.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet controller for verifying database connectivity and system health.
 * Adheres strictly to the CAMS architecture: handles HTTP request/response only,
 * delegating all logic to PingService.
 */
@WebServlet(name = "PingServlet", urlPatterns = {"/api/ping", "/api/health"})
public class PingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(PingServlet.class.getName());

    private final PingService pingService;

    public PingServlet() {
        this.pingService = new PingService();
    }

    public PingServlet(PingService pingService) {
        this.pingService = pingService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String clientMsg = request.getParameter("message");

        // Informative check if password placeholder hasn't been set yet
        if (!pingService.isDbConfigured()) {
            Map<String, Object> configInfo = new HashMap<>();
            configInfo.put("jdbcUrl", pingService.getConfiguredJdbcUrl());
            configInfo.put("dbUser", pingService.getConfiguredDbUser());
            configInfo.put("hint", "Please edit src/main/resources/db.properties and set db.password to your Oracle password.");

            JsonUtil.sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Oracle password not set in db.properties. Please configure db.password and try again.");
            return;
        }

        try {
            // Delegate directly to the Service layer
            PingResult result = pingService.checkDatabaseHealth(clientMsg);
            JsonUtil.sendSuccess(response, "Oracle Database round-trip verification successful!", result);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database connection failed during ping", e);
            String safeError = "Failed to connect to Oracle Database: " + e.getMessage();
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, safeError);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during ping", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }
}
