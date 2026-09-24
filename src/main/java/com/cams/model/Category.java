package com.cams.model;

import java.sql.Timestamp;

/**
 * Domain entity representing an Asset Classification Category in the CATEGORIES table.
 */
public class Category {

    private String categoryId;
    private String categoryName;
    private String active; // 'Y' or 'N'
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Category() {
        this.active = "Y";
    }

    public Category(String categoryId, String categoryName, String active) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.active = active != null ? active : "Y";
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
