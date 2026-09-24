package com.cams.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * Domain entity representing a physical or digital Asset in CAMS.
 */
public class Asset implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assetId;
    private String assetName;
    private String category;
    private String department;
    private Date purchaseDate;
    private BigDecimal purchaseCost;
    private String vendorId;
    private Date warrantyExpiry;
    private String status; // AVAILABLE, ISSUED, UNDER_MAINTENANCE, DISPOSED
    private String location;
    private Date disposedDate;
    private String disposalReason;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Object details;

    public Asset() {
    }

    public Asset(String assetId, String assetName, String category, String department,
                 Date purchaseDate, BigDecimal purchaseCost, String vendorId,
                 Date warrantyExpiry, String status, String location) {
        this.assetId = assetId;
        this.assetName = assetName;
        this.category = category;
        this.department = department;
        this.purchaseDate = purchaseDate;
        this.purchaseCost = purchaseCost;
        this.vendorId = vendorId;
        this.warrantyExpiry = warrantyExpiry;
        this.status = status;
        this.location = location;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
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

    public Date getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(Date purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public Date getWarrantyExpiry() {
        return warrantyExpiry;
    }

    public void setWarrantyExpiry(Date warrantyExpiry) {
        this.warrantyExpiry = warrantyExpiry;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getDisposedDate() {
        return disposedDate;
    }

    public void setDisposedDate(Date disposedDate) {
        this.disposedDate = disposedDate;
    }

    public String getDisposalReason() {
        return disposalReason;
    }

    public void setDisposalReason(String disposalReason) {
        this.disposalReason = disposalReason;
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

    public Object getDetails() {
        return details;
    }

    public void setDetails(Object details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return "Asset{" +
                "assetId='" + assetId + '\'' +
                ", assetName='" + assetName + '\'' +
                ", category='" + category + '\'' +
                ", department='" + department + '\'' +
                ", purchaseDate=" + purchaseDate +
                ", purchaseCost=" + purchaseCost +
                ", status='" + status + '\'' +
                ", location='" + location + '\'' +
                '}';
    }
}
