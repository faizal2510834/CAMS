package com.cams.service;

import com.cams.dao.*;
import com.cams.model.AssetIssue;
import com.cams.model.PagedResult;
import com.cams.model.User;
import com.cams.util.DBConnection;

import com.cams.model.Maintenance;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IssueServiceImpl implements IssueService {

    private static final Logger LOGGER = Logger.getLogger(IssueServiceImpl.class.getName());

    private final IssueDAO issueDAO;
    private final AssetDAO assetDAO;
    private final DepartmentDAO departmentDAO;
    private final UserDAO userDAO;
    private final MaintenanceDAO maintenanceDAO;

    public IssueServiceImpl() {
        this(new IssueDAOImpl(), new AssetDAOImpl(), new DepartmentDAOImpl(), new UserDAOImpl(), new MaintenanceDAOImpl());
    }

    public IssueServiceImpl(IssueDAO issueDAO, AssetDAO assetDAO, DepartmentDAO departmentDAO, UserDAO userDAO) {
        this(issueDAO, assetDAO, departmentDAO, userDAO, new MaintenanceDAOImpl());
    }

    public IssueServiceImpl(IssueDAO issueDAO, AssetDAO assetDAO, DepartmentDAO departmentDAO, UserDAO userDAO, MaintenanceDAO maintenanceDAO) {
        this.issueDAO = issueDAO;
        this.assetDAO = assetDAO;
        this.departmentDAO = departmentDAO;
        this.userDAO = userDAO;
        this.maintenanceDAO = maintenanceDAO;
    }

    @Override
    public AssetIssue createIssue(User sessionUser, AssetIssue issue) throws SQLException {
        if (sessionUser == null) {
            throw new IssueForbiddenException("Authentication required to issue equipment");
        }

        String role = sessionUser.getRole();
        if (!"Administrator".equalsIgnoreCase(role) && !"Faculty".equalsIgnoreCase(role)) {
            throw new IssueForbiddenException("Access denied: Role '" + role + "' cannot issue equipment");
        }

        if (issue == null) {
            throw new IssueValidationException("Issue request payload cannot be empty");
        }
        if (issue.getAssetId() == null || issue.getAssetId().trim().isEmpty()) {
            throw new IssueValidationException("Asset ID is required");
        }
        String assetId = issue.getAssetId().trim();

        // Faculty can only issue to themselves
        if ("Faculty".equalsIgnoreCase(role)) {
            issue.setIssuedToUserId(sessionUser.getUserId());
        } else {
            // Administrator workflow
            if (issue.getIssuedToUserId() == null || issue.getIssuedToUserId() <= 0) {
                issue.setIssuedToUserId(sessionUser.getUserId());
            } else {
                if (userDAO.findById(issue.getIssuedToUserId()).isEmpty()) {
                    throw new IssueValidationException("Recipient user with ID " + issue.getIssuedToUserId() + " does not exist");
                }
            }
        }

        // Validate department FK against DEPARTMENTS table
        if (issue.getIssuedToDepartment() == null || issue.getIssuedToDepartment().trim().isEmpty()) {
            throw new IssueValidationException("Issuing Department is required");
        }
        String deptId = issue.getIssuedToDepartment().trim();
        if (!departmentDAO.isActive(deptId)) {
            throw new IssueValidationException("Department '" + deptId + "' does not exist or is inactive");
        }
        issue.setIssuedToDepartment(deptId);

        // Date validation
        Date today = Date.valueOf(LocalDate.now());
        if (issue.getIssueDate() == null) {
            issue.setIssueDate(today);
        } else if (issue.getIssueDate().after(today)) {
            throw new IssueValidationException("Issue date cannot be in the future");
        }

        if (issue.getExpectedReturnDate() != null && issue.getExpectedReturnDate().before(issue.getIssueDate())) {
            throw new IssueValidationException("Expected return date cannot be earlier than issue date");
        }

        // Atomic Transaction: changeStatus(AVAILABLE -> ISSUED) + insert into ASSET_ISSUES
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int statusUpdated = assetDAO.changeStatus(con, assetId, "AVAILABLE", "ISSUED");
                if (statusUpdated == 0) {
                    throw new IssueConflictException("Asset '" + assetId + "' is not AVAILABLE for issuing (current status changed or already issued)");
                }

                String issueId = "ISS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                issue.setIssueId(issueId);
                issue.setStatus("ISSUED");
                issue.setIssuedBy(sessionUser.getUsername());

                boolean inserted = issueDAO.insert(con, issue);
                if (!inserted) {
                    throw new SQLException("Failed to record asset issuance");
                }

                con.commit();
                LOGGER.info("Asset " + assetId + " successfully issued to user " + issue.getIssuedToUserId() + " (Issue ID: " + issueId + ")");
                return issueDAO.findById(issueId).orElse(issue);

            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed during asset issuance", rollbackEx);
                }
                if (e instanceof IssueConflictException || e instanceof IssueValidationException) {
                    throw (RuntimeException) e;
                }
                if (e instanceof AssetConflictException) {
                    throw new IssueConflictException(e.getMessage());
                }
                throw new RuntimeException("Transaction error during asset issuance: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public AssetIssue returnIssue(User sessionUser, String issueId, String conditionOnReturn, String returnRemarks) throws SQLException {
        if (sessionUser == null) {
            throw new IssueForbiddenException("Authentication required to return equipment");
        }

        String role = sessionUser.getRole();
        if (!"Administrator".equalsIgnoreCase(role) && !"Faculty".equalsIgnoreCase(role)) {
            throw new IssueForbiddenException("Access denied: Role '" + role + "' cannot return equipment");
        }

        if (issueId == null || issueId.trim().isEmpty()) {
            throw new IssueValidationException("Issue ID is required");
        }

        AssetIssue existing = issueDAO.findById(issueId.trim())
                .orElseThrow(() -> new IssueNotFoundException("Issue record not found: " + issueId));

        // Ownership check: Faculty can only return equipment issued to themselves
        if ("Faculty".equalsIgnoreCase(role)) {
            if (existing.getIssuedToUserId() == null || !existing.getIssuedToUserId().equals(sessionUser.getUserId())) {
                throw new IssueForbiddenException("Access denied: Faculty can only return equipment issued to themselves");
            }
        }

        // Concurrency & Atomic Return Transaction
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Date today = Date.valueOf(LocalDate.now());
                int affected = issueDAO.markReturned(con, issueId.trim(), today, conditionOnReturn, returnRemarks, sessionUser.getUsername());
                if (affected == 0) {
                    throw new IssueConflictException("Issue transaction '" + issueId + "' has already been returned or was concurrently modified");
                }

                // Determine target asset status based on returned condition
                String targetStatus = "AVAILABLE";
                if (conditionOnReturn != null) {
                    String condUpper = conditionOnReturn.trim().toUpperCase();
                    if (condUpper.contains("DAMAGED") || condUpper.contains("FAULTY") || condUpper.contains("NEEDS_REPAIR")) {
                        targetStatus = "UNDER_MAINTENANCE";
                    }
                }

                int statusUpdated = assetDAO.changeStatus(con, existing.getAssetId(), "ISSUED", targetStatus);
                if (statusUpdated == 0) {
                    throw new IssueConflictException("Conflict updating asset status for '" + existing.getAssetId() + "'");
                }

                // If asset is returned in damaged condition, automatically create a maintenance ticket
                if ("UNDER_MAINTENANCE".equals(targetStatus)) {
                    Maintenance mnt = new Maintenance();
                    String maintId = "MNT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    mnt.setMaintenanceId(maintId);
                    mnt.setAssetId(existing.getAssetId());
                    mnt.setMaintenanceDate(today);
                    mnt.setScheduledDate(today);
                    String faultDesc = (returnRemarks != null && !returnRemarks.trim().isEmpty())
                            ? returnRemarks.trim()
                            : "Asset returned in " + (conditionOnReturn != null ? conditionOnReturn.trim() : "DAMAGED") + " condition.";
                    if (faultDesc.length() > 500) {
                        faultDesc = faultDesc.substring(0, 497) + "...";
                    }
                    mnt.setFaultDescription(faultDesc);
                    mnt.setStatus("SCHEDULED");
                    mnt.setCreatedBy(sessionUser.getUsername());
                    mnt.setIssueId(issueId.trim());

                    maintenanceDAO.create(con, mnt);
                    LOGGER.info("Auto-created maintenance ticket " + maintId + " for damaged asset " + existing.getAssetId());
                }

                con.commit();
                LOGGER.info("Equipment return processed for Issue " + issueId + ". Asset " + existing.getAssetId() + " transitioned to " + targetStatus);
                return issueDAO.findById(issueId.trim()).orElse(existing);

            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed during asset return", rollbackEx);
                }
                if (e instanceof IssueConflictException || e instanceof IssueValidationException || e instanceof IssueForbiddenException) {
                    throw (RuntimeException) e;
                }
                if (e instanceof AssetConflictException) {
                    throw new IssueConflictException(e.getMessage());
                }
                throw new RuntimeException("Transaction error during asset return: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public AssetIssue getIssueById(User sessionUser, String issueId) throws SQLException {
        if (sessionUser == null) {
            throw new IssueForbiddenException("Authentication required");
        }

        AssetIssue issue = issueDAO.findById(issueId.trim())
                .orElseThrow(() -> new IssueNotFoundException("Issue record not found: " + issueId));

        // Ownership check: Faculty cannot view another Faculty member's transaction
        if ("Faculty".equalsIgnoreCase(sessionUser.getRole())) {
            if (issue.getIssuedToUserId() == null || !issue.getIssuedToUserId().equals(sessionUser.getUserId())) {
                throw new IssueForbiddenException("Access denied: You can only view your own equipment transactions");
            }
        }

        return issue;
    }

    @Override
    public PagedResult<AssetIssue> searchIssues(User sessionUser, String status, String assetId,
                                                Long userId, String deptId, int page, int size) throws SQLException {
        if (sessionUser == null) {
            throw new IssueForbiddenException("Authentication required");
        }

        // Faculty is strictly locked to their own transactions
        if ("Faculty".equalsIgnoreCase(sessionUser.getRole())) {
            userId = sessionUser.getUserId();
        }

        return issueDAO.search(status, assetId, userId, deptId, page, size);
    }
}
