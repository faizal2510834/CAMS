package com.cams.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for formatting and sending consistent JSON responses across all Servlets.
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .serializeNulls()
            .create();

    private JsonUtil() {
        // Prevent instantiation
    }

    public static Gson getGson() {
        return GSON;
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Sends a structured JSON success response with default HTTP 200 OK.
     */
    public static void sendSuccess(HttpServletResponse response, String message, Object data) throws IOException {
        sendSuccess(response, HttpServletResponse.SC_OK, message, data);
    }

    /**
     * Sends a structured JSON success response with specified HTTP status code.
     */
    public static void sendSuccess(HttpServletResponse response, int statusCode, String message, Object data) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setStatus(statusCode);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(GSON.toJson(body));
            writer.flush();
        }
    }

    /**
     * Sends a structured JSON error response with appropriate HTTP status code.
     */
    public static void sendError(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setStatus(statusCode);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(GSON.toJson(body));
            writer.flush();
        }
    }
}
