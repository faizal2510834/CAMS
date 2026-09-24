package com.cams.controller;

import com.cams.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test endpoint to verify Role-Based Access Control (RBAC) across:
 * - /api/admin/summary      (Administrator only)
 * - /api/faculty/summary    (Faculty only)
 * - /api/technical/summary  (Technical Staff only)
 */
@WebServlet(name = "RoleTestServlet", urlPatterns = {
        "/api/admin/summary",
        "/api/faculty/summary",
        "/api/technical/summary"
})
public class RoleTestServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        HttpSession session = request.getSession(false);

        String username = (session != null) ? (String) session.getAttribute("username") : "anonymous";
        String role = (session != null) ? (String) session.getAttribute("role") : "none";
        String department = (session != null) ? (String) session.getAttribute("department") : "none";

        Map<String, Object> data = new HashMap<>();
        data.put("requestedEndpoint", path);
        data.put("authenticatedUser", username);
        data.put("role", role);
        data.put("department", department);
        data.put("rbacStatus", "AUTHORIZED_BY_FILTER");

        if (path.endsWith("/admin/summary")) {
            data.put("module", "Administrator Control Hub");
            data.put("totalAssetsCount", 142);
            data.put("pendingMaintenanceApprovals", 3);
            data.put("activeSystemUsers", 18);
            data.put("systemHealth", "OPTIMAL");
            JsonUtil.sendSuccess(response, "Administrator access verified successfully!", data);
        } else if (path.endsWith("/faculty/summary")) {
            data.put("module", "Faculty Self-Service Portal");
            data.put("departmentAssets", 24);
            data.put("activeRequisitions", 2);
            data.put("labRoomAssignments", "CS-Lab-301");
            JsonUtil.sendSuccess(response, "Faculty access verified successfully!", data);
        } else if (path.endsWith("/technical/summary")) {
            data.put("module", "Technical Maintenance Workspace");
            data.put("openWorkOrders", 5);
            data.put("urgentRepairs", 1);
            data.put("scheduledInspections", 8);
            JsonUtil.sendSuccess(response, "Technical Staff access verified successfully!", data);
        } else {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "Resource not found");
        }
    }
}
