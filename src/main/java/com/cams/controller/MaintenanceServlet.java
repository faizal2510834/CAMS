package com.cams.controller;

import com.cams.model.Maintenance;
import com.cams.model.PagedResult;
import com.cams.model.User;
import com.cams.service.*;
import com.cams.util.JsonUtil;
import com.google.gson.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller handling Maintenance Management REST API endpoints (/api/maintenance/*).
 */
@WebServlet(urlPatterns = {"/api/maintenance", "/api/maintenance/*"})
public class MaintenanceServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(MaintenanceServlet.class.getName());
    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd")
            .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, typeOfT, context) -> {
                try {
                    String str = json.getAsString();
                    if (str == null || str.trim().isEmpty()) return null;
                    return Date.valueOf(str.trim());
                } catch (Exception e) {
                    throw new RuntimeException("Invalid date format, expected YYYY-MM-DD: " + json.getAsString());
                }
            })
            .registerTypeAdapter(Date.class, (JsonSerializer<Date>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd").format(src)))
            .create();

    private MaintenanceService maintenanceService;

    @Override
    public void init() throws ServletException {
        this.maintenanceService = new MaintenanceServiceImpl();
    }

    public void setMaintenanceService(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    private User getSessionUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return null;
        }
        User user = new User();
        Object uid = session.getAttribute("userId");
        if (uid instanceof Number) {
            user.setUserId(((Number) uid).longValue());
        }
        user.setUsername((String) session.getAttribute("username"));
        user.setRole((String) session.getAttribute("role"));
        user.setDepartment((String) session.getAttribute("department"));
        user.setName((String) session.getAttribute("name"));
        return user;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = getSessionUser(req);
        if (user == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please log in.");
            return;
        }

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || "/".equals(pathInfo)) {
                // GET /api/maintenance
                String status = req.getParameter("status");
                String assetId = req.getParameter("assetId");
                String technician = req.getParameter("technician");
                int page = parseInt(req.getParameter("page"), 1);
                int size = parseInt(req.getParameter("size"), 10);

                PagedResult<Maintenance> result = maintenanceService.searchMaintenance(user, status, assetId, technician, page, size);
                BigDecimal totalCost = maintenanceService.getTotalCost(user, status, assetId, technician);

                Map<String, Object> data = new HashMap<>();
                data.put("items", result.getItems());
                data.put("page", result.getPage());
                data.put("size", result.getSize());
                data.put("totalItems", result.getTotalItems());
                data.put("totalPages", result.getTotalPages());
                data.put("totalCost", totalCost);

                JsonUtil.sendSuccess(resp, "Maintenance tickets retrieved successfully", data);
                return;
            }

            if ("/summary".equalsIgnoreCase(pathInfo)) {
                // GET /api/maintenance/summary
                BigDecimal totalCost = maintenanceService.getTotalCost(user, null, null, null);
                Map<String, Object> data = new HashMap<>();
                data.put("totalMaintenanceCost", totalCost);
                JsonUtil.sendSuccess(resp, "Maintenance summary retrieved", data);
                return;
            }

            // GET /api/maintenance/{id}
            String id = pathInfo.substring(1).trim();
            Maintenance m = maintenanceService.getMaintenanceById(user, id);
            JsonUtil.sendSuccess(resp, "Maintenance ticket retrieved successfully", m);

        } catch (MaintenanceForbiddenException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (MaintenanceNotFoundException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (MaintenanceValidationException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in GET /api/maintenance", e);
            JsonUtil.sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error retrieving maintenance records: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = getSessionUser(req);
        if (user == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please log in.");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && !"/".equals(pathInfo)) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Invalid POST endpoint: " + pathInfo);
            return;
        }

        try {
            String body = readBody(req);
            Maintenance m = GSON.fromJson(body, Maintenance.class);

            Maintenance created = maintenanceService.scheduleMaintenance(user, m);
            JsonUtil.sendSuccess(resp, HttpServletResponse.SC_CREATED, "Maintenance scheduled successfully", created);

        } catch (MaintenanceForbiddenException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (MaintenanceConflictException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (MaintenanceValidationException | JsonSyntaxException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in POST /api/maintenance", e);
            JsonUtil.sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error scheduling maintenance: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = getSessionUser(req);
        if (user == null) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please log in.");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Maintenance ID required in path (e.g. /api/maintenance/{id})");
            return;
        }

        String id = pathInfo.substring(1).trim();

        try {
            String body = readBody(req);
            Maintenance updateData = GSON.fromJson(body, Maintenance.class);

            Maintenance updated = maintenanceService.updateMaintenance(user, id, updateData);
            JsonUtil.sendSuccess(resp, "Maintenance record updated successfully", updated);

        } catch (MaintenanceForbiddenException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (MaintenanceConflictException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (MaintenanceNotFoundException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (MaintenanceValidationException | JsonSyntaxException e) {
            JsonUtil.sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in PUT /api/maintenance/" + id, e);
            JsonUtil.sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error updating maintenance: " + e.getMessage());
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private int parseInt(String val, int defaultVal) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        try {
            int parsed = Integer.parseInt(val.trim());
            return Math.max(1, parsed);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
