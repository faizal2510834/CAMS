package com.cams.model;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * Domain entity representing an Asset Issuance and Return record in ASSET_ISSUES.
 */
public class AssetIssue implements Serializable {

    private static final long serialVersionUID = 1L;

    private String issueId;
    private String assetId;
    private String assetName; // Joined
    private Long issuedToUserId;
    private String issuedToUserName; // Joined
    private String issuedToDepartment;
    private String departmentName; // Joined
    private Date issueDate;
    private Date expectedReturnDate;
    private Date actualReturnDate;
    private String status; // 'ISSUED', 'RETURNED'
    private String conditionOnIssue;
    private String conditionOnReturn;
    private String remarks;
    private String returnRemarks;
    private String issuedBy;
    private String returnedTo;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public AssetIssue() {
        this.status = "ISSUED";
        this.conditionOnIssue = "GOOD";
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
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

    public Long getIssuedToUserId() {
        return issuedToUserId;
    }

    public void setIssuedToUserId(Long issuedToUserId) {
        this.issuedToUserId = issuedToUserId;
    }

    public String getIssuedToUserName() {
        return issuedToUserName;
    }

    public void setIssuedToUserName(String issuedToUserName) {
        this.issuedToUserName = issuedToUserName;
    }

    public String getIssuedToDepartment() {
        return issuedToDepartment;
    }

    public void setIssuedToDepartment(String issuedToDepartment) {
        this.issuedToDepartment = issuedToDepartment;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Date getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(Date issueDate) {
        this.issueDate = issueDate;
    }

    public Date getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public void setExpectedReturnDate(Date expectedReturnDate) {
        this.expectedReturnDate = expectedReturnDate;
    }

    public Date getActualReturnDate() {
        return actualReturnDate;
    }

    public void setActualReturnDate(Date actualReturnDate) {
        this.actualReturnDate = actualReturnDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConditionOnIssue() {
        return conditionOnIssue;
    }

    public void setConditionOnIssue(String conditionOnIssue) {
        this.conditionOnIssue = conditionOnIssue;
    }

    public String getConditionOnReturn() {
        return conditionOnReturn;
    }

    public void setConditionOnReturn(String conditionOnReturn) {
        this.conditionOnReturn = conditionOnReturn;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getReturnRemarks() {
        return returnRemarks;
    }

    public void setReturnRemarks(String returnRemarks) {
        this.returnRemarks = returnRemarks;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public String getReturnedTo() {
        return returnedTo;
    }

    public void setReturnedTo(String returnedTo) {
        this.returnedTo = returnedTo;
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
