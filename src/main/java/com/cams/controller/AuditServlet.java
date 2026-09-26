package com.cams.controller;

import com.cams.model.AuditQueryCriteria;
import com.cams.model.AuditRecord;
import com.cams.model.AuditSummary;
import com.cams.model.PagedResult;
import com.cams.service.AssetNotFoundException;
import com.cams.service.AssetValidationException;
import com.cams.service.AuditService;
import com.cams.service.AuditServiceImpl;
import com.cams.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller servlet for CAMS Physical Inventory Audits (Module 8).
 * Endpoints:
 * - POST /api/audits          (Conduct verification)
 * - GET  /api/audits          (Search/report history)
 * - GET  /api/audits/summary  (Summary counters)
 */
@WebServlet(name = "AuditServlet", urlPatterns = {"/api/audits", "/api/audits/*"})
public class AuditServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AuditServlet.class.getName());

    private final AuditService auditService;

    public AuditServlet() {
        this(new AuditServiceImpl());
    }

    public AuditServlet(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();

        try {
            if ("/summary".equalsIgnoreCase(pathInfo)) {
                AuditSummary summary = auditService.getAuditSummary();
                JsonUtil.sendSuccess(response, "Audit summary counters retrieved successfully", summary);
                return;
            }

            AuditQueryCriteria criteria = parseCriteria(request);
            PagedResult<AuditRecord> result = auditService.getAudits(criteria);
            JsonUtil.sendSuccess(response, "Audit history retrieved successfully", result);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retrieving audit records", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database query failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in AuditServlet GET", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        String username = (session != null) ? (String) session.getAttribute("username") : null;
        if (username == null || username.trim().isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to conduct inventory audits.");
            return;
        }

        try (BufferedReader reader = request.getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            String assetId = json.has("assetId") && !json.get("assetId").isJsonNull() ? json.get("assetId").getAsString() : null;
            String status = json.has("status") && !json.get("status").isJsonNull() ? json.get("status").getAsString() : null;
            String remarks = json.has("remarks") && !json.get("remarks").isJsonNull() ? json.get("remarks").getAsString() : null;

            AuditRecord created = auditService.recordAudit(assetId, status, remarks, username);
            JsonUtil.sendSuccess(response, HttpServletResponse.SC_CREATED, "Asset inventory verification recorded successfully", created);

        } catch (AssetValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (AssetNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error recording audit", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database transaction failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in AuditServlet POST", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private AuditQueryCriteria parseCriteria(HttpServletRequest request) {
        AuditQueryCriteria c = new AuditQueryCriteria();

        c.setAssetId(request.getParameter("assetId"));
        c.setStatus(request.getParameter("status"));
        c.setDepartment(request.getParameter("department"));
        c.setVerifiedBy(request.getParameter("verifiedBy"));
        c.setStartDate(request.getParameter("startDate"));
        c.setEndDate(request.getParameter("endDate"));

        String p = request.getParameter("page");
        if (p != null) {
            try { c.setPage(Integer.parseInt(p)); } catch (NumberFormatException ignored) {}
        }

        String s = request.getParameter("size");
        if (s != null) {
            try { c.setSize(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
        }

        String sortBy = request.getParameter("sortBy");
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            c.setSortBy(sortBy.trim());
        }

        String sortOrder = request.getParameter("sortOrder");
        if (sortOrder != null && !sortOrder.trim().isEmpty()) {
            c.setSortOrder(sortOrder.trim());
        }

        return c;
    }
}
