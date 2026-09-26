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
        // GET /api/assets/{id}/depreciation -> Administrator only
        // POST / PUT / DELETE (Add/Edit/Retire) -> Administrator only
        // Standard GET (Search/View/Options) -> All 3 roles permitted
        if (path.startsWith("/api/assets")) {
            if (path.endsWith("/depreciation")) {
                if (!"Administrator".equalsIgnoreCase(userRole)) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Administrator role required to view asset depreciation details.");
                    return;
                }
            } else if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                if (!"Administrator".equalsIgnoreCase(userRole)) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Administrator role required to create, modify, or retire assets.");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }

        // Special handling for Issue & Return Management API RBAC:
        // GET -> All 3 roles permitted (Administrator, Faculty, Technical Staff)
        // POST / PUT -> Administrator and Faculty permitted; Technical Staff blocked (403)
        if (path.startsWith("/api/issues")) {
            if ("POST".equals(method) || "PUT".equals(method)) {
                if ("Technical Staff".equalsIgnoreCase(userRole)) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Technical Staff cannot issue or return equipment.");
                    return;
                }
                if (!"Administrator".equalsIgnoreCase(userRole) && !"Faculty".equalsIgnoreCase(userRole)) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Administrator or Faculty role required to issue or return equipment.");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }

        // Special handling for Maintenance Management API & UI RBAC:
        // Technical Staff and Administrator permitted; Faculty strictly blocked (403)
        if (path.startsWith("/api/maintenance") || path.startsWith("/pages/maintenance") || path.startsWith("/pages/technical/maintenance.html")) {
            if (!"Administrator".equalsIgnoreCase(userRole) && !"Technical Staff".equalsIgnoreCase(userRole)) {
                if (path.startsWith("/api/")) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Only Technical Staff and Administrators can access maintenance management.");
                } else {
                    String redirectUrl = httpRequest.getContextPath() + "/pages/access-denied.html" +
                            "?required=Technical+Staff&current=" + URLEncoder.encode(userRole != null ? userRole : "Anonymous", StandardCharsets.UTF_8);
                    httpResponse.sendRedirect(redirectUrl);
                }
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // Special handling for Physical Inventory Audit API & UI RBAC (Module 8):
        // Technical Staff and Administrator permitted for UI and reports
        // Faculty strictly blocked (403 Forbidden)
        if (path.startsWith("/api/audits") || path.startsWith("/pages/technical/audit.html") || path.startsWith("/pages/audit")) {
            if (!"Administrator".equalsIgnoreCase(userRole) && !"Technical Staff".equalsIgnoreCase(userRole)) {
                if (path.startsWith("/api/")) {
                    JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                            "Access denied: Role 'Technical Staff' or 'Administrator' required to access inventory audits.");
                } else {
                    String redirectUrl = httpRequest.getContextPath() + "/pages/access-denied.html" +
                            "?required=Technical+Staff&current=" + URLEncoder.encode(userRole != null ? userRole : "Anonymous", StandardCharsets.UTF_8);
                    httpResponse.sendRedirect(redirectUrl);
                }
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // Special handling for Technical Dashboard API (Module 10):
        // Technical Staff and Administrator permitted; Faculty strictly blocked (403)
        if (path.startsWith("/api/dashboard/technical")) {
            if (!"Administrator".equalsIgnoreCase(userRole) && !"Technical Staff".equalsIgnoreCase(userRole)) {
                JsonUtil.sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "Access denied: Technical Staff or Administrator role required.");
                return;
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
        if (path.startsWith("/api/vendors") || path.startsWith("/api/purchases") ||
            path.startsWith("/api/departments") || path.startsWith("/api/locations") || path.startsWith("/api/categories") ||
            path.startsWith("/api/depreciation") || path.startsWith("/api/users") ||
            path.startsWith("/api/reports") || path.startsWith("/api/dashboard/admin")) {
            return "Administrator";
        }
        if (path.startsWith("/pages/vendors") || path.startsWith("/pages/purchases") || path.startsWith("/pages/master-data")) {
            return "Administrator";
        }
        if (path.startsWith("/pages/faculty/") || path.startsWith("/api/faculty/") || path.startsWith("/api/dashboard/faculty")) {
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
