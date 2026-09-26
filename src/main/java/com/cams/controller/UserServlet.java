package com.cams.controller;

import com.cams.model.User;
import com.cams.service.UserConflictException;
import com.cams.service.UserNotFoundException;
import com.cams.service.UserService;
import com.cams.service.UserServiceImpl;
import com.cams.service.UserValidationException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller servlet for Module 9: User Management.
 * Endpoints:
 * - GET    /api/users (search/filter list)
 * - GET    /api/users/{id} (single user)
 * - POST   /api/users (create user)
 * - PUT    /api/users/{id} (update details)
 * - PUT    /api/users/{id}/deactivate (soft-delete account)
 * - PUT    /api/users/{id}/activate (reactivate account)
 * - PUT    /api/users/{id}/reset-password (reset password)
 */
@WebServlet(name = "UserServlet", urlPatterns = {"/api/users", "/api/users/*"})
public class UserServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(UserServlet.class.getName());

    private final UserService userService;

    public UserServlet() {
        this(new UserServiceImpl());
    }

    public UserServlet(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || "/".equals(pathInfo)) {
                // List users with search / filter / pagination
                String search = request.getParameter("search");
                String role = request.getParameter("role");
                String department = request.getParameter("department");
                String active = request.getParameter("active");

                int page = 1;
                int size = 20;
                try {
                    String pageParam = request.getParameter("page");
                    if (pageParam != null) page = Integer.parseInt(pageParam);
                    String sizeParam = request.getParameter("size");
                    if (sizeParam != null) size = Integer.parseInt(sizeParam);
                } catch (NumberFormatException ignored) {}

                List<User> users = userService.getUsers(search, role, department, active, page, size);
                long total = userService.getTotalUsers(search, role, department, active);
                int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;

                Map<String, Object> data = new HashMap<>();
                data.put("users", users);
                data.put("totalItems", total);
                data.put("currentPage", page);
                data.put("pageSize", size);
                data.put("totalPages", totalPages);

                JsonUtil.sendSuccess(response, "Users retrieved successfully", data);
            } else {
                // GET /api/users/{id}
                String[] parts = pathInfo.split("/");
                if (parts.length >= 2) {
                    Long userId = parseUserId(parts[1]);
                    User user = userService.getUserById(userId);
                    Map<String, Object> data = new HashMap<>();
                    data.put("user", user);
                    JsonUtil.sendSuccess(response, "User retrieved successfully", data);
                } else {
                    JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid user ID path");
                }
            }
        } catch (UserValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (UserNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in UserServlet.doGet", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !"/".equals(pathInfo)) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + pathInfo);
            return;
        }

        try {
            JsonObject json = parseJsonBody(request);
            if (json == null) {
                JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid or missing JSON payload");
                return;
            }

            String name = json.has("name") ? json.get("name").getAsString() : null;
            String role = json.has("role") ? json.get("role").getAsString() : null;
            String department = json.has("department") ? json.get("department").getAsString() : null;
            String username = json.has("username") ? json.get("username").getAsString() : null;
            String password = json.has("password") ? json.get("password").getAsString() : null;

            User newUser = new User();
            newUser.setName(name);
            newUser.setRole(role);
            newUser.setDepartment(department);
            newUser.setUsername(username);

            User created = userService.createUser(newUser, password);
            JsonUtil.sendSuccess(response, HttpServletResponse.SC_CREATED, "User created successfully", created);

        } catch (UserValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (UserConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in UserServlet.doPost", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "User ID is required in URL path");
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length < 2) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid user URL path");
            return;
        }

        try {
            Long userId = parseUserId(parts[1]);

            if (parts.length == 2) {
                // PUT /api/users/{id} -> Update details
                JsonObject json = parseJsonBody(request);
                if (json == null) {
                    JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing JSON payload");
                    return;
                }

                String name = json.has("name") ? json.get("name").getAsString() : null;
                String role = json.has("role") ? json.get("role").getAsString() : null;
                String department = json.has("department") ? json.get("department").getAsString() : null;

                User updatedData = new User();
                updatedData.setName(name);
                updatedData.setRole(role);
                updatedData.setDepartment(department);

                User updated = userService.updateUser(userId, updatedData);
                JsonUtil.sendSuccess(response, "User updated successfully", updated);

            } else if (parts.length == 3 && "deactivate".equalsIgnoreCase(parts[2])) {
                // PUT /api/users/{id}/deactivate
                HttpSession session = request.getSession(false);
                Long currentAdminId = session != null ? (Long) session.getAttribute("userId") : null;

                userService.deactivateUser(userId, currentAdminId);
                JsonUtil.sendSuccess(response, "User account deactivated successfully", null);

            } else if (parts.length == 3 && "activate".equalsIgnoreCase(parts[2])) {
                // PUT /api/users/{id}/activate
                userService.activateUser(userId);
                JsonUtil.sendSuccess(response, "User account reactivated successfully", null);

            } else if (parts.length == 3 && "reset-password".equalsIgnoreCase(parts[2])) {
                // PUT /api/users/{id}/reset-password
                JsonObject json = parseJsonBody(request);
                String customPassword = null;
                if (json != null) {
                    if (json.has("temporaryPassword")) {
                        customPassword = json.get("temporaryPassword").getAsString();
                    } else if (json.has("newPassword")) {
                        customPassword = json.get("newPassword").getAsString();
                    }
                }

                String tempPassword = userService.resetPassword(userId, customPassword);
                Map<String, Object> data = new HashMap<>();
                data.put("temporaryPassword", tempPassword);
                data.put("mustChangePassword", true);
                data.put("userId", userId);

                JsonUtil.sendSuccess(response, "Password reset successfully", data);

            } else {
                JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown action: " + parts[2]);
            }

        } catch (UserValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (UserConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (UserNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error in UserServlet.doPut", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    private Long parseUserId(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            throw new UserValidationException("Invalid numeric User ID: " + str);
        }
    }

    private JsonObject parseJsonBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
