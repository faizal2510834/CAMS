package com.cams.model;

import java.util.Set;

/**
 * Encapsulates search filters, pagination offsets, and safe whitelisted sorting
 * for querying the ASSETS table.
 */
public class AssetQueryCriteria {

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
            "ASSET_ID", "ASSET_NAME", "CATEGORY", "DEPARTMENT",
            "PURCHASE_DATE", "PURCHASE_COST", "STATUS", "LOCATION", "CREATED_AT"
    );

    private String keyword;
    private String category;
    private String department;
    private String status;
    private String location;
    private boolean includeDisposed = false;

    private int page = 1;
    private int size = 10;
    private String sortBy = "CREATED_AT";
    private String sortOrder = "DESC";

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = (category != null && !category.trim().isEmpty()) ? category.trim() : null;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = (department != null && !department.trim().isEmpty()) ? department.trim() : null;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim() : null;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = (location != null && !location.trim().isEmpty()) ? location.trim() : null;
    }

    public boolean isIncludeDisposed() {
        return includeDisposed;
    }

    public void setIncludeDisposed(boolean includeDisposed) {
        this.includeDisposed = includeDisposed;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size <= 0) {
            this.size = 10;
        } else {
            this.size = Math.min(size, 100); // capped at 100 per SRS
        }
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        if (sortBy != null && ALLOWED_SORT_COLUMNS.contains(sortBy.trim().toUpperCase())) {
            this.sortBy = sortBy.trim().toUpperCase();
        } else {
            this.sortBy = "CREATED_AT";
        }
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        if ("ASC".equalsIgnoreCase(sortOrder)) {
            this.sortOrder = "ASC";
        } else {
            this.sortOrder = "DESC";
        }
    }

    public int getOffset() {
        return (this.page - 1) * this.size;
    }
}
