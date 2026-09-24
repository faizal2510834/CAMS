package com.cams.controller;

import com.cams.model.Department;
import com.cams.service.MasterDataConflictException;
import com.cams.service.MasterDataNotFoundException;
import com.cams.service.MasterDataService;
import com.cams.service.MasterDataServiceImpl;
import com.cams.service.MasterDataValidationException;
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller handling Department REST API endpoints (/api/departments/*).
 */
@WebServlet(urlPatterns = {"/api/departments", "/api/departments/*"})
public class DepartmentServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(DepartmentServlet.class.getName());
    private static final Gson GSON = new Gson();

    private MasterDataService masterDataService;

    @Override
    public void init() throws ServletException {
        this.masterDataService = new MasterDataServiceImpl();
    }

    public void setMasterDataService(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                String active = request.getParameter("active");
                List<Department> list = masterDataService.getAllDepartments(active);
                JsonUtil.sendSuccess(response, "Departments retrieved successfully", list);
            } else {
                String id = pathInfo.substring(1);
                if (id.contains("/")) {
                    id = id.substring(0, id.indexOf("/"));
                }
                Department dept = masterDataService.getDepartmentById(id);
                JsonUtil.sendSuccess(response, "Department details retrieved successfully", dept);
            }
        } catch (MasterDataNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (MasterDataValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error querying departments", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: " + e.getMessage());
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
            Department dept = GSON.fromJson(json, Department.class);

            Department created = masterDataService.createDepartment(dept);
            JsonUtil.sendSuccess(response, HttpServletResponse.SC_CREATED, "Department created successfully", created);

        } catch (MasterDataConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (MasterDataValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating department", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Department ID required in path");
            return;
        }

        String path = pathInfo.substring(1);

        try {
            if (path.endsWith("/deactivate")) {
                String id = path.substring(0, path.length() - "/deactivate".length());
                masterDataService.deactivateDepartment(id);
                JsonUtil.sendSuccess(response, "Department deactivated successfully", null);
            } else {
                String id = path;
                JsonObject json = parseJsonBody(request);
                Department dept = GSON.fromJson(json, Department.class);

                Department updated = masterDataService.updateDepartment(id, dept);
                JsonUtil.sendSuccess(response, "Department updated successfully", updated);
            }
        } catch (MasterDataNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (MasterDataConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (MasterDataValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating department", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    private JsonObject parseJsonBody(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
