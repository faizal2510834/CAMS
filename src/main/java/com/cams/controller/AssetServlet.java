package com.cams.controller;

import com.cams.model.Asset;
import com.cams.model.AssetQueryCriteria;
import com.cams.model.PagedResult;
import com.cams.service.AssetConflictException;
import com.cams.service.AssetNotFoundException;
import com.cams.service.AssetService;
import com.cams.service.AssetServiceImpl;
import com.cams.service.AssetValidationException;
import com.cams.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller servlet for CAMS Asset Management (Module 2).
 * Handles:
 * - GET    /api/assets/options
 * - GET    /api/assets
 * - GET    /api/assets/{id}
 * - POST   /api/assets
 * - PUT    /api/assets/{id}
 * - PUT    /api/assets/{id}/retire
 */
@WebServlet(name = "AssetServlet", urlPatterns = {"/api/assets", "/api/assets/*"})
public class AssetServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AssetServlet.class.getName());

    private final AssetService assetService;

    public AssetServlet() {
        this(new AssetServiceImpl());
    }

    public AssetServlet(AssetService assetService) {
        this.assetService = assetService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = getSubPath(request);

        try {
            if ("/options".equalsIgnoreCase(pathInfo)) {
                JsonUtil.sendSuccess(response, "Asset dropdown options retrieved", assetService.getOptions());
                return;
            }

            if (pathInfo != null && pathInfo.length() > 1) {
                // GET /api/assets/{id}
                String assetId = pathInfo.substring(1);
                Asset asset = assetService.getAssetById(assetId);
                JsonUtil.sendSuccess(response, "Asset retrieved successfully", asset);
                return;
            }

            // GET /api/assets (List with pagination and filters)
            AssetQueryCriteria criteria = parseQueryCriteria(request);
            PagedResult<Asset> result = assetService.getAssets(criteria);
            JsonUtil.sendSuccess(response, "Assets retrieved successfully", result);

        } catch (AssetNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error querying assets", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database query failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in AssetServlet GET", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = getSubPath(request);
        if (pathInfo != null && !pathInfo.isEmpty() && !"/".equals(pathInfo)) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + request.getRequestURI());
            return;
        }

        try {
            Asset asset = parseAssetFromJson(request);
            Asset created = assetService.addAsset(asset);
            response.setStatus(HttpServletResponse.SC_CREATED);
            JsonUtil.sendSuccess(response, "Asset created successfully", created);

        } catch (AssetValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (AssetConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error adding asset", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in AssetServlet POST", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = getSubPath(request);
        if (pathInfo == null || pathInfo.length() <= 1) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Asset ID must be specified in the URL path");
            return;
        }

        // Check if PUT /api/assets/{id}/retire
        if (pathInfo.endsWith("/retire")) {
            // Extract {id} from /{id}/retire
            String remaining = pathInfo.substring(1, pathInfo.length() - "/retire".length());
            handleRetire(remaining, request, response);
            return;
        }

        // Regular PUT /api/assets/{id}
        String assetId = pathInfo.substring(1);
        handleUpdate(assetId, request, response);
    }

    private void handleRetire(String assetId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            String reason = null;
            try (BufferedReader reader = request.getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("reason") && !json.get("reason").isJsonNull()) {
                    reason = json.get("reason").getAsString();
                }
            } catch (Exception e) {
                // Reason is optional
            }

            assetService.retireAsset(assetId, reason);
            JsonUtil.sendSuccess(response, "Asset retired successfully", null);

        } catch (AssetNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (AssetConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error retiring asset", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error retiring asset", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private void handleUpdate(String assetId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            Asset updateData = parseAssetFromJson(request);
            Asset updated = assetService.updateAsset(assetId, updateData);
            JsonUtil.sendSuccess(response, "Asset updated successfully", updated);

        } catch (AssetValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (AssetConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (AssetNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating asset", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error updating asset", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private String getSubPath(HttpServletRequest request) {
        String base = request.getContextPath() + "/api/assets";
        String uri = request.getRequestURI();
        if (uri.startsWith(base)) {
            return uri.substring(base.length());
        }
        return request.getPathInfo();
    }

    private AssetQueryCriteria parseQueryCriteria(HttpServletRequest request) {
        AssetQueryCriteria c = new AssetQueryCriteria();

        String p = request.getParameter("page");
        if (p != null) {
            try { c.setPage(Integer.parseInt(p)); } catch (NumberFormatException ignored) {}
        }

        String s = request.getParameter("size");
        if (s != null) {
            try { c.setSize(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
        }

        c.setKeyword(request.getParameter("keyword"));
        c.setCategory(request.getParameter("category"));
        c.setDepartment(request.getParameter("department"));
        c.setStatus(request.getParameter("status"));
        c.setLocation(request.getParameter("location"));
        c.setIncludeDisposed("true".equalsIgnoreCase(request.getParameter("includeDisposed")));
        c.setSortBy(request.getParameter("sortBy"));
        c.setSortOrder(request.getParameter("sortOrder"));

        return c;
    }

    private Asset parseAssetFromJson(HttpServletRequest request) throws AssetValidationException, IOException {
        try (BufferedReader reader = request.getReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            Asset asset = new Asset();
            if (json.has("assetId") && !json.get("assetId").isJsonNull()) {
                asset.setAssetId(json.get("assetId").getAsString().trim());
            }
            if (json.has("assetName") && !json.get("assetName").isJsonNull()) {
                asset.setAssetName(json.get("assetName").getAsString().trim());
            }
            if (json.has("category") && !json.get("category").isJsonNull()) {
                asset.setCategory(json.get("category").getAsString().trim());
            }
            if (json.has("department") && !json.get("department").isJsonNull()) {
                asset.setDepartment(json.get("department").getAsString().trim());
            }
            if (json.has("purchaseDate") && !json.get("purchaseDate").isJsonNull()) {
                String dStr = json.get("purchaseDate").getAsString().trim();
                try {
                    asset.setPurchaseDate(Date.valueOf(dStr));
                } catch (IllegalArgumentException e) {
                    throw new AssetValidationException("Invalid purchase date format: " + dStr + ". Expected YYYY-MM-DD");
                }
            }
            if (json.has("purchaseCost") && !json.get("purchaseCost").isJsonNull()) {
                try {
                    asset.setPurchaseCost(json.get("purchaseCost").getAsBigDecimal());
                } catch (Exception e) {
                    throw new AssetValidationException("Invalid purchase cost format");
                }
            }
            if (json.has("vendorId") && !json.get("vendorId").isJsonNull()) {
                asset.setVendorId(json.get("vendorId").getAsString().trim());
            }
            if (json.has("warrantyExpiry") && !json.get("warrantyExpiry").isJsonNull()
                    && !json.get("warrantyExpiry").getAsString().trim().isEmpty()) {
                String wStr = json.get("warrantyExpiry").getAsString().trim();
                try {
                    asset.setWarrantyExpiry(Date.valueOf(wStr));
                } catch (IllegalArgumentException e) {
                    throw new AssetValidationException("Invalid warranty expiry date format: " + wStr + ". Expected YYYY-MM-DD");
                }
            }
            if (json.has("location") && !json.get("location").isJsonNull()) {
                asset.setLocation(json.get("location").getAsString().trim());
            }

            return asset;
        } catch (AssetValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new AssetValidationException("Malformed JSON request body: " + e.getMessage());
        }
    }
}
