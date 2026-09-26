package com.cams.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Domain entity representing an inventory verification record (Module 8).
 */
public class AuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String auditId;
    private String assetId;
    private Timestamp auditDate;
    private String verifiedBy;
    private String status; // VERIFIED, MISSING, MISLOCATED
    private String remarks;
    private Timestamp createdAt;

    // Joined fields from ASSETS for reporting & display
    private String assetName;
    private String category;
    private String department;
    private String currentLocation;
    private String assetStatus;

    public AuditRecord() {
    }

    public AuditRecord(String auditId, String assetId, String verifiedBy, String status, String remarks) {
        this.auditId = auditId;
        this.assetId = assetId;
        this.verifiedBy = verifiedBy;
        this.status = status;
        this.remarks = remarks;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public Timestamp getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(Timestamp auditDate) {
        this.auditDate = auditDate;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public String getAssetStatus() {
        return assetStatus;
    }

    public void setAssetStatus(String assetStatus) {
        this.assetStatus = assetStatus;
    }
}
