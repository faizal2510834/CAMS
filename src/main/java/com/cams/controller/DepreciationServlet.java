package com.cams.controller;

import com.cams.model.AssetDepreciation;
import com.cams.model.DepreciationSummary;
import com.cams.service.AssetNotFoundException;
import com.cams.service.DepreciationService;
import com.cams.service.DepreciationServiceImpl;
import com.cams.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller servlet for CAMS Depreciation Management (Module 7).
 * Endpoints:
 * - GET /api/depreciation/summary
 * - GET /api/depreciation/{id}
 */
@WebServlet(name = "DepreciationServlet", urlPatterns = {"/api/depreciation", "/api/depreciation/*"})
public class DepreciationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DepreciationServlet.class.getName());

    private final DepreciationService depreciationService;

    public DepreciationServlet() {
        this(new DepreciationServiceImpl());
    }

    public DepreciationServlet(DepreciationService depreciationService) {
        this.depreciationService = depreciationService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo) || "/summary".equalsIgnoreCase(pathInfo)) {
                DepreciationSummary summary = depreciationService.getDepreciationSummary();
                JsonUtil.sendSuccess(response, "Campus depreciation summary retrieved successfully", summary);
                return;
            }

            // GET /api/depreciation/{id}
            String assetId = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            AssetDepreciation dep = depreciationService.getAssetDepreciation(assetId);
            JsonUtil.sendSuccess(response, "Asset depreciation details retrieved successfully", dep);

        } catch (AssetNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error computing depreciation", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in DepreciationServlet", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }
}
