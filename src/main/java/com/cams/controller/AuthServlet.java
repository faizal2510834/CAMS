package com.cams.controller;

import com.cams.model.User;
import com.cams.service.AuthService;
import com.cams.service.AuthServiceImpl;
import com.cams.service.AuthenticationException;
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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling user authentication lifecycle:
 * - POST /api/auth/login
 * - POST /api/auth/logout
 * - GET  /api/auth/session
 */
@WebServlet(name = "AuthServlet", urlPatterns = {"/api/auth/login", "/api/auth/logout", "/api/auth/session"})
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AuthServlet.class.getName());

    private final AuthService authService;

    public AuthServlet() {
        this(new AuthServiceImpl());
    }

    public AuthServlet(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            // Self-bootstrap USERS table and seed accounts if needed
            authService.ensureSchemaAndSeed();
            LOGGER.info("AuthServlet initialized: USERS schema and initial seed verified.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Auto-initialization of USERS table skipped or failed: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (path.endsWith("/login")) {
            handleLogin(request, response);
        } else if (path.endsWith("/logout")) {
            handleLogout(request, response);
        } else {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + path);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (path.endsWith("/session")) {
            handleSession(request, response);
        } else {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found: " + path);
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String username = null;
        String password = null;

        // Support both JSON body and standard Form POST
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            try (BufferedReader reader = request.getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("username")) {
                    username = json.get("username").getAsString();
                }
                if (json.has("password")) {
                    password = json.get("password").getAsString();
                }
            } catch (Exception e) {
                JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON request body");
                return;
            }
        } else {
            username = request.getParameter("username");
            password = request.getParameter("password");
        }

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid username or password");
            return;
        }

        try {
            User safeUser = authService.authenticate(username, password);

            // Invalidate any pre-existing session to mitigate session fixation attacks
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }

            // Create fresh authenticated session
            HttpSession session = request.getSession(true);
            session.setAttribute("userId", safeUser.getUserId());
            session.setAttribute("username", safeUser.getUsername());
            session.setAttribute("name", safeUser.getName());
            session.setAttribute("role", safeUser.getRole());
            session.setAttribute("department", safeUser.getDepartment());

            String redirectUrl = authService.getDashboardUrlForRole(safeUser.getRole());

            Map<String, Object> data = new HashMap<>();
            data.put("user", safeUser);
            data.put("redirectUrl", redirectUrl);

            JsonUtil.sendSuccess(response, "Login successful", data);

        } catch (AuthenticationException e) {
            // Clean 401 without stack trace
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during authentication", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database service temporarily unavailable");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during login", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error during authentication");
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        JsonUtil.sendSuccess(response, "Logged out successfully", null);
    }

    private void handleSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            JsonUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "No active session found");
            return;
        }

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", session.getAttribute("userId"));
        sessionData.put("username", session.getAttribute("username"));
        sessionData.put("name", session.getAttribute("name"));
        sessionData.put("role", session.getAttribute("role"));
        sessionData.put("department", session.getAttribute("department"));
        sessionData.put("creationTime", session.getCreationTime());
        sessionData.put("lastAccessedTime", session.getLastAccessedTime());
        sessionData.put("maxInactiveInterval", session.getMaxInactiveInterval());

        JsonUtil.sendSuccess(response, "Session active", sessionData);
    }
}
