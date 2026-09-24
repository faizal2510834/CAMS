package com.cams.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * Model representing a procurement transaction in the PURCHASES table.
 */
public class Purchase {

    private String purchaseId;
    private String assetId;
    private String vendorId;
    private String invoiceNumber;
    private Date purchaseDate;
    private BigDecimal cost;
    private String status; // PENDING, APPROVED, REJECTED
    private String approvedBy;
    private Timestamp approvedAt;
    private Timestamp createdAt;

    // Joined fields for display convenience
    private String assetName;
    private String vendorName;

    public Purchase() {
        this.status = "PENDING";
    }

    public Purchase(String purchaseId, String assetId, String vendorId, String invoiceNumber, Date purchaseDate, BigDecimal cost) {
        this.purchaseId = purchaseId;
        this.assetId = assetId;
        this.vendorId = vendorId;
        this.invoiceNumber = invoiceNumber;
        this.purchaseDate = purchaseDate;
        this.cost = cost;
        this.status = "PENDING";
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(String purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Date getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(Date purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
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

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }
}
