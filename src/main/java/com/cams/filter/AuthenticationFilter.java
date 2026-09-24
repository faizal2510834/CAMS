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
 * AuthenticationFilter enforces that users have an active, authenticated session
 * before accessing protected pages and API resources.
 */
public class AuthenticationFilter implements Filter {

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

        // Check if path is public
        if (isPublicResource(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Validate session
        HttpSession session = httpRequest.getSession(false);
        boolean isAuthenticated = (session != null && session.getAttribute("userId") != null);

        if (isAuthenticated) {
            chain.doFilter(request, response);
            return;
        }

        // Unauthenticated request handling
        if (path.startsWith("/api/")) {
            JsonUtil.sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication required. Please log in.");
        } else {
            String redirectUrl = httpRequest.getContextPath() + "/pages/login.html";
            if (!path.isEmpty() && !"/".equals(path)) {
                redirectUrl += "?redirect=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
            }
            httpResponse.sendRedirect(redirectUrl);
        }
    }

    private boolean isPublicResource(String path) {
        if (path == null || path.isEmpty() || "/".equals(path) || "/index.html".equals(path)) {
            return true;
        }
        if (path.equals("/pages/login.html") || path.equals("/pages/access-denied.html")) {
            return true;
        }
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/") || path.startsWith("/fonts/")) {
            return true;
        }
        if (path.equals("/api/auth/login") || path.equals("/api/ping") || path.equals("/api/health")) {
            return true;
        }
        return false;
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
