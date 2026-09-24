package com.cams.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Master data constants for CAMS Asset Management (Module 2).
 * Enforces strict whitelist validation for Departments, Categories, Locations, and Statuses.
 */
public final class AssetConstants {

    private AssetConstants() {
        // Prevent instantiation
    }

    public static final List<String> DEPARTMENTS = List.of(
            "CSE", "IT", "ECE", "EEE", "MECH", "CIVIL"
    );

    public static final List<String> CATEGORIES = List.of(
            "Computer", "Laboratory Equipment", "Classroom Asset", "Furniture", "Other"
    );

    public static final List<String> LOCATIONS = List.of(
            "CSE Lab 1", "IT Lab 1", "ECE Lab 1", "Room 101"
    );

    public static final List<String> STATUSES = List.of(
            "AVAILABLE", "ISSUED", "UNDER_MAINTENANCE", "DISPOSED"
    );

    public static boolean isValidDepartment(String department) {
        return department != null && DEPARTMENTS.contains(department.trim());
    }

    public static boolean isValidCategory(String category) {
        return category != null && CATEGORIES.contains(category.trim());
    }

    public static boolean isValidLocation(String location) {
        return location != null && LOCATIONS.contains(location.trim());
    }

    public static boolean isValidStatus(String status) {
        return status != null && STATUSES.contains(status.trim());
    }

    /**
     * Returns an unmodifiable map containing all allowed options for dropdown feeds.
     */
    public static Map<String, List<String>> getOptions() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("departments", DEPARTMENTS);
        options.put("categories", CATEGORIES);
        options.put("locations", LOCATIONS);
        options.put("statuses", STATUSES);
        return Collections.unmodifiableMap(options);
    }
}
