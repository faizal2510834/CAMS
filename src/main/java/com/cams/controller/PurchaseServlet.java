package com.cams.controller;

import com.cams.model.PagedResult;
import com.cams.model.Purchase;
import com.cams.service.PurchaseConflictException;
import com.cams.service.PurchaseNotFoundException;
import com.cams.service.PurchaseService;
import com.cams.service.PurchaseServiceImpl;
import com.cams.service.PurchaseValidationException;
import com.cams.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller handling Purchase procurement and approval REST API endpoints (/api/purchases/*).
 */
@WebServlet(urlPatterns = {"/api/purchases", "/api/purchases/*"})
public class PurchaseServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(PurchaseServlet.class.getName());

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd")
            .registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, typeOfT, context) -> {
                try {
                    return Date.valueOf(json.getAsString());
                } catch (Exception e) {
                    throw new RuntimeException("Invalid date format, expected YYYY-MM-DD: " + json.getAsString());
                }
            })
            .registerTypeAdapter(Date.class, (JsonSerializer<Date>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd").format(src)))
            .create();

    private PurchaseService purchaseService;

    @Override
    public void init() throws ServletException {
        this.purchaseService = new PurchaseServiceImpl();
    }

    public void setPurchaseService(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                // GET /api/purchases (paginated & filtered)
                String status = request.getParameter("status");
                String vendorId = request.getParameter("vendorId");
                int page = parseIntParam(request.getParameter("page"), 1);
                int size = parseIntParam(request.getParameter("size"), 10);

                PagedResult<Purchase> result = purchaseService.getPurchases(status, vendorId, page, size);
                JsonUtil.sendSuccess(response, "Purchases retrieved successfully", result);

            } else {
                // GET /api/purchases/{id}
                String purchaseId = pathInfo.substring(1);
                if (purchaseId.contains("/")) {
                    purchaseId = purchaseId.substring(0, purchaseId.indexOf("/"));
                }
                Purchase purchase = purchaseService.getPurchaseById(purchaseId);
                JsonUtil.sendSuccess(response, "Purchase details retrieved successfully", purchase);
            }

        } catch (PurchaseNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (PurchaseValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error querying purchases", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error querying purchases", e);
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
            Purchase purchase = GSON.fromJson(json, Purchase.class);

            Purchase created = purchaseService.createPurchase(purchase);
            response.setStatus(HttpServletResponse.SC_CREATED);
            JsonUtil.sendSuccess(response, "Purchase recorded successfully as PENDING", created);

        } catch (PurchaseValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (PurchaseConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (PurchaseNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error recording purchase", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error recording purchase", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Purchase ID required in path");
            return;
        }

        String path = pathInfo.substring(1);

        try {
            HttpSession session = request.getSession(false);
            String sessionUser = (session != null && session.getAttribute("username") != null)
                    ? (String) session.getAttribute("username")
                    : "Administrator";

            if (path.endsWith("/approve")) {
                // PUT /api/purchases/{id}/approve
                String purchaseId = path.substring(0, path.length() - "/approve".length());
                Purchase approved = purchaseService.approvePurchase(purchaseId, sessionUser);
                JsonUtil.sendSuccess(response, "Purchase approved and vendor linked to asset successfully", approved);

            } else if (path.endsWith("/reject")) {
                // PUT /api/purchases/{id}/reject
                String purchaseId = path.substring(0, path.length() - "/reject".length());
                String reason = null;
                try {
                    JsonObject json = parseJsonBody(request);
                    if (json.has("reason") && !json.get("reason").isJsonNull()) {
                        reason = json.get("reason").getAsString();
                    }
                } catch (Exception ignored) {}

                Purchase rejected = purchaseService.rejectPurchase(purchaseId, reason);
                JsonUtil.sendSuccess(response, "Purchase rejected successfully", rejected);

            } else {
                JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid action for purchase route. Expected /approve or /reject");
            }

        } catch (PurchaseNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (PurchaseConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (PurchaseValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error updating purchase status", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error updating purchase status", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
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
