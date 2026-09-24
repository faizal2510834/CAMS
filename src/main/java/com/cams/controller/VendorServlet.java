package com.cams.controller;

import com.cams.model.PagedResult;
import com.cams.model.Vendor;
import com.cams.service.VendorConflictException;
import com.cams.service.VendorNotFoundException;
import com.cams.service.VendorService;
import com.cams.service.VendorServiceImpl;
import com.cams.service.VendorValidationException;
import com.cams.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller handling Vendor REST API endpoints (/api/vendors/*).
 */
@WebServlet(urlPatterns = {"/api/vendors", "/api/vendors/*"})
public class VendorServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(VendorServlet.class.getName());
    private static final Gson GSON = new Gson();

    private VendorService vendorService;

    @Override
    public void init() throws ServletException {
        this.vendorService = new VendorServiceImpl();
    }

    public void setVendorService(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                // Check if requesting active list
                String activeOnly = request.getParameter("activeOnly");
                if ("true".equalsIgnoreCase(activeOnly)) {
                    List<Vendor> activeList = vendorService.getActiveVendors();
                    JsonUtil.sendSuccess(response, "Active vendors retrieved", activeList);
                    return;
                }

                // GET /api/vendors (paginated & filtered)
                String search = request.getParameter("search");
                String active = request.getParameter("active");
                int page = parseIntParam(request.getParameter("page"), 1);
                int size = parseIntParam(request.getParameter("size"), 10);

                PagedResult<Vendor> result = vendorService.getVendors(search, active, page, size);
                JsonUtil.sendSuccess(response, "Vendors retrieved successfully", result);

            } else {
                // GET /api/vendors/{id}
                String vendorId = pathInfo.substring(1);
                if (vendorId.contains("/")) {
                    vendorId = vendorId.substring(0, vendorId.indexOf("/"));
                }
                Vendor vendor = vendorService.getVendorById(vendorId);
                JsonUtil.sendSuccess(response, "Vendor details retrieved successfully", vendor);
            }

        } catch (VendorNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (VendorValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error querying vendors", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error querying vendors", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.equals("/") && !pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST not supported on sub-paths");
            return;
        }

        try {
            JsonObject json = parseJsonBody(request);
            Vendor vendor = GSON.fromJson(json, Vendor.class);

            Vendor created = vendorService.createVendor(vendor);
            response.setStatus(HttpServletResponse.SC_CREATED);
            JsonUtil.sendSuccess(response, "Vendor created successfully", created);

        } catch (VendorValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (VendorConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error creating vendor", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error creating vendor", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Vendor ID required in path");
            return;
        }

        String path = pathInfo.substring(1);

        try {
            if (path.endsWith("/deactivate")) {
                // PUT /api/vendors/{id}/deactivate
                String vendorId = path.substring(0, path.length() - "/deactivate".length());
                vendorService.deactivateVendor(vendorId);
                JsonUtil.sendSuccess(response, "Vendor deactivated successfully", null);

            } else {
                // PUT /api/vendors/{id}
                String vendorId = path;
                JsonObject json = parseJsonBody(request);
                Vendor vendor = GSON.fromJson(json, Vendor.class);

                Vendor updated = vendorService.updateVendor(vendorId, vendor);
                JsonUtil.sendSuccess(response, "Vendor updated successfully", updated);
            }

        } catch (VendorNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (VendorValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (VendorConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating vendor", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error updating vendor", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    private JsonObject parseJsonBody(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private int parseIntParam(String val, int defaultVal) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
