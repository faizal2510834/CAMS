package com.cams.controller;

import com.cams.model.AssetIssue;
import com.cams.model.PagedResult;
import com.cams.model.User;
import com.cams.service.*;
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
 * Controller handling Issue & Return REST API endpoints (/api/issues/*).
 */
@WebServlet(urlPatterns = {"/api/issues", "/api/issues/*"})
public class IssueServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(IssueServlet.class.getName());
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

    private IssueService issueService;

    @Override
    public void init() throws ServletException {
        this.issueService = new IssueServiceImpl();
    }

    public void setIssueService(IssueService issueService) {
        this.issueService = issueService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User sessionUser = getSessionUser(request);
        if (sessionUser == null) {
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                // List & search
                String status = request.getParameter("status");
                String assetId = request.getParameter("assetId");
                String deptId = request.getParameter("department");
                Long userId = parseLong(request.getParameter("userId"));
                int page = parseIntParam(request.getParameter("page"), 1);
                int size = parseIntParam(request.getParameter("size"), 10);

                PagedResult<AssetIssue> result = issueService.searchIssues(sessionUser, status, assetId, userId, deptId, page, size);
                JsonUtil.sendSuccess(response, "Asset issues retrieved successfully", result);

            } else {
                // GET /api/issues/{id}
                String issueId = pathInfo.substring(1);
                if (issueId.contains("/")) {
                    issueId = issueId.substring(0, issueId.indexOf("/"));
                }
                AssetIssue issue = issueService.getIssueById(sessionUser, issueId);
                JsonUtil.sendSuccess(response, "Issue details retrieved successfully", issue);
            }

        } catch (IssueNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (IssueForbiddenException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (IssueValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in IssueServlet GET", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in IssueServlet GET", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User sessionUser = getSessionUser(request);
        if (sessionUser == null) {
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.equals("/") && !pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST not supported on sub-paths");
            return;
        }

        try {
            JsonObject json = parseJsonBody(request);
            AssetIssue issue = GSON.fromJson(json, AssetIssue.class);

            AssetIssue created = issueService.createIssue(sessionUser, issue);
            JsonUtil.sendSuccess(response, HttpServletResponse.SC_CREATED, "Equipment issued successfully", created);

        } catch (IssueForbiddenException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (IssueConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (IssueValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in IssueServlet POST", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in IssueServlet POST", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User sessionUser = getSessionUser(request);
        if (sessionUser == null) {
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Issue ID required in path");
            return;
        }

        String path = pathInfo.substring(1);

        try {
            if (path.endsWith("/return")) {
                // PUT /api/issues/{id}/return
                String issueId = path.substring(0, path.length() - "/return".length());
                JsonObject json = parseJsonBody(request);

                String condition = json.has("conditionOnReturn") && !json.get("conditionOnReturn").isJsonNull()
                        ? json.get("conditionOnReturn").getAsString() : null;
                String remarks = json.has("returnRemarks") && !json.get("returnRemarks").isJsonNull()
                        ? json.get("returnRemarks").getAsString() : null;

                AssetIssue returned = issueService.returnIssue(sessionUser, issueId, condition, remarks);
                JsonUtil.sendSuccess(response, "Equipment returned successfully", returned);

            } else {
                JsonUtil.sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Unsupported PUT path on issues");
            }

        } catch (IssueNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (IssueForbiddenException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (IssueConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (IssueValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in IssueServlet PUT", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in IssueServlet PUT", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    private User getSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
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

    private JsonObject parseJsonBody(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private Long parseLong(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return null; }
    }

    private int parseIntParam(String val, int defaultVal) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
