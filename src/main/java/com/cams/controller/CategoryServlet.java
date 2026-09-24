package com.cams.controller;

import com.cams.model.Category;
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
 * Controller handling Category REST API endpoints (/api/categories/*).
 */
@WebServlet(urlPatterns = {"/api/categories", "/api/categories/*"})
public class CategoryServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(CategoryServlet.class.getName());
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
                List<Category> list = masterDataService.getAllCategories(active);
                JsonUtil.sendSuccess(response, "Categories retrieved successfully", list);
            } else {
                String id = pathInfo.substring(1);
                if (id.contains("/")) {
                    id = id.substring(0, id.indexOf("/"));
                }
                Category cat = masterDataService.getCategoryById(id);
                JsonUtil.sendSuccess(response, "Category details retrieved successfully", cat);
            }
        } catch (MasterDataNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (MasterDataValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error querying categories", e);
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
            Category cat = GSON.fromJson(json, Category.class);

            Category created = masterDataService.createCategory(cat);
            JsonUtil.sendSuccess(response, HttpServletResponse.SC_CREATED, "Category created successfully", created);

        } catch (MasterDataConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (MasterDataValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating category", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Category ID required in path");
            return;
        }

        String path = pathInfo.substring(1);

        try {
            if (path.endsWith("/deactivate")) {
                String id = path.substring(0, path.length() - "/deactivate".length());
                masterDataService.deactivateCategory(id);
                JsonUtil.sendSuccess(response, "Category deactivated successfully", null);
            } else {
                String id = path;
                JsonObject json = parseJsonBody(request);
                Category cat = GSON.fromJson(json, Category.class);

                Category updated = masterDataService.updateCategory(id, cat);
                JsonUtil.sendSuccess(response, "Category updated successfully", updated);
            }
        } catch (MasterDataNotFoundException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (MasterDataConflictException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (MasterDataValidationException e) {
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating category", e);
            JsonUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request payload: " + e.getMessage());
        }
    }

    private JsonObject parseJsonBody(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
