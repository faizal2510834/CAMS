package com.cams.filter;

import com.cams.util.JsonUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * AuthorizationFilter enforces Role-Based Access Control (RBAC).
 * Verifies that the authenticated user possesses the specific role required for target routes.
 */
public class AuthorizationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        String method = httpRequest.getMethod().toUpperCase();

        HttpSession session = httpRequest.getSession(false);
        String userRole = (session != null) ? (String) session.getAttribute("role") : null;

        // Special handling for Asset Management API RBAC:
        // GET (Search/View/Options) -> All 3 roles permitted
        // POST / PUT / DELETE (Add/Edit/Retire) -> Administrator only
        if (path.startsWith("/api/assets")) {
            if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                if (!"Administrator".equalsIgnoreCase(userRole)) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Administrator role required to create, modify, or retire assets.");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }

        String requiredRole = determineRequiredRole(path);

        // If this route does not require a specific role, proceed
        if (requiredRole == null) {
            chain.doFilter(request, response);
            return;
        }

        // Check if user has the required role
        if (userRole != null && userRole.equalsIgnoreCase(requiredRole)) {
            chain.doFilter(request, response);
            return;
        }

        // Access Denied: User role does not match required role
        if (path.startsWith("/api/")) {
            JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: Role '" + requiredRole + "' is required to access this resource.");
        } else {
            String redirectUrl = httpRequest.getContextPath() + "/pages/access-denied.html" +
                    "?required=" + URLEncoder.encode(requiredRole, StandardCharsets.UTF_8) +
                    "&current=" + URLEncoder.encode(userRole != null ? userRole : "Anonymous", StandardCharsets.UTF_8);
            httpResponse.sendRedirect(redirectUrl);
        }
    }

    private String determineRequiredRole(String path) {
        if (path.startsWith("/pages/admin/") || path.startsWith("/api/admin/")) {
            return "Administrator";
        }
        if (path.startsWith("/pages/faculty/") || path.startsWith("/api/faculty/")) {
            return "Faculty";
        }
        if (path.startsWith("/pages/technical/") || path.startsWith("/api/technical/")) {
            return "Technical Staff";
        }
        return null;
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
