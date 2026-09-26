package com.cams.service;

import com.cams.dao.AssetDAO;
import com.cams.dao.AssetDAOImpl;
import com.cams.dao.MaintenanceDAO;
import com.cams.dao.MaintenanceDAOImpl;
import com.cams.model.Asset;
import com.cams.model.Maintenance;
import com.cams.model.PagedResult;
import com.cams.model.User;
import com.cams.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MaintenanceServiceImpl implements MaintenanceService {

    private static final Logger LOGGER = Logger.getLogger(MaintenanceServiceImpl.class.getName());

    private final MaintenanceDAO maintenanceDAO;
    private final AssetDAO assetDAO;

    public MaintenanceServiceImpl() {
        this.maintenanceDAO = new MaintenanceDAOImpl();
        this.assetDAO = new AssetDAOImpl();
    }

    public MaintenanceServiceImpl(MaintenanceDAO maintenanceDAO, AssetDAO assetDAO) {
        this.maintenanceDAO = maintenanceDAO;
        this.assetDAO = assetDAO;
    }

    private void checkAuthorizedStaff(User sessionUser) {
        if (sessionUser == null) {
            throw new MaintenanceForbiddenException("Authentication required");
        }
        String role = sessionUser.getRole();
        if (!"Administrator".equalsIgnoreCase(role) && !"Technical Staff".equalsIgnoreCase(role)) {
            throw new MaintenanceForbiddenException("Access denied: Only Technical Staff and Administrators can access maintenance management");
        }
    }

    @Override
    public Maintenance scheduleMaintenance(User sessionUser, Maintenance m) throws SQLException {
        checkAuthorizedStaff(sessionUser);

        if (m == null) {
            throw new MaintenanceValidationException("Maintenance payload cannot be empty");
        }
        if (m.getAssetId() == null || m.getAssetId().trim().isEmpty()) {
            throw new MaintenanceValidationException("Asset ID is required for scheduling maintenance");
        }
        if (m.getFaultDescription() == null || m.getFaultDescription().trim().isEmpty()) {
            throw new MaintenanceValidationException("Fault description or maintenance scope is required");
        }
        if (m.getFaultDescription().trim().length() > 500) {
            throw new MaintenanceValidationException("Fault description cannot exceed 500 characters");
        }
        if (m.getScheduledDate() == null) {
            throw new MaintenanceValidationException("Scheduled date is required");
        }

        String assetId = m.getAssetId().trim();
        Asset asset = assetDAO.findById(assetId)
                .orElseThrow(() -> new MaintenanceValidationException("Asset not found: " + assetId));

        if ("DISPOSED".equalsIgnoreCase(asset.getStatus())) {
            throw new MaintenanceConflictException("Cannot schedule maintenance for DISPOSED (retired) asset: " + assetId);
        }
        if ("ISSUED".equalsIgnoreCase(asset.getStatus())) {
            throw new MaintenanceConflictException("Cannot schedule maintenance on an ISSUED asset '" + assetId + "'. It must be returned to inventory first.");
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // If asset is already UNDER_MAINTENANCE, verify no active ticket exists
                if ("UNDER_MAINTENANCE".equalsIgnoreCase(asset.getStatus())) {
                    var activeOpt = maintenanceDAO.findActiveByAssetId(con, assetId);
                    if (activeOpt.isPresent()) {
                        throw new MaintenanceConflictException("Asset '" + assetId + "' is already UNDER_MAINTENANCE with an active ticket (" + activeOpt.get().getMaintenanceId() + ")");
                    }
                } else if ("AVAILABLE".equalsIgnoreCase(asset.getStatus())) {
                    // Transition asset to UNDER_MAINTENANCE
                    int rows = assetDAO.changeStatus(con, assetId, "AVAILABLE", "UNDER_MAINTENANCE");
                    if (rows == 0) {
                        throw new MaintenanceConflictException("Conflict updating asset '" + assetId + "': status was concurrently modified");
                    }
                }

                String maintId = "MNT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                m.setMaintenanceId(maintId);
                m.setAssetId(assetId);
                m.setStatus("SCHEDULED");
                m.setCreatedBy(sessionUser.getUsername());
                if (m.getMaintenanceDate() == null) {
                    m.setMaintenanceDate(Date.valueOf(LocalDate.now()));
                }
                if (m.getCost() == null) {
                    m.setCost(BigDecimal.ZERO);
                } else if (m.getCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new MaintenanceValidationException("Maintenance cost must be greater than or equal to zero");
                }

                maintenanceDAO.create(con, m);
                con.commit();
                LOGGER.info("Maintenance scheduled: " + maintId + " for asset " + assetId + " by " + sessionUser.getUsername());
                return maintenanceDAO.findById(maintId).orElse(m);

            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed during maintenance scheduling", rollbackEx);
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Transaction error during maintenance scheduling: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public Maintenance updateMaintenance(User sessionUser, String maintenanceId, Maintenance updateData) throws SQLException {
        checkAuthorizedStaff(sessionUser);

        if (maintenanceId == null || maintenanceId.trim().isEmpty()) {
            throw new MaintenanceValidationException("Maintenance ID is required");
        }
        if (updateData == null) {
            throw new MaintenanceValidationException("Update data cannot be empty");
        }

        String mid = maintenanceId.trim();

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Maintenance existing = maintenanceDAO.findById(con, mid)
                        .orElseThrow(() -> new MaintenanceNotFoundException("Maintenance ticket not found: " + mid));

                // Terminal status check: COMPLETED tickets cannot be modified
                if ("COMPLETED".equalsIgnoreCase(existing.getStatus())) {
                    throw new MaintenanceConflictException("Maintenance ticket '" + mid + "' is already COMPLETED and cannot be modified.");
                }

                // Status validation
                String newStatus = updateData.getStatus() != null ? updateData.getStatus().trim().toUpperCase() : existing.getStatus();
                List<String> validStatuses = Arrays.asList("SCHEDULED", "IN_PROGRESS", "COMPLETED", "REQUIRES_FURTHER_REPAIR");
                if (!validStatuses.contains(newStatus)) {
                    throw new MaintenanceValidationException("Invalid maintenance status: " + newStatus);
                }

                // Cost validation
                if (updateData.getCost() != null) {
                    if (updateData.getCost().compareTo(BigDecimal.ZERO) < 0) {
                        throw new MaintenanceValidationException("Maintenance cost must be greater than or equal to zero");
                    }
                    existing.setCost(updateData.getCost());
                }

                if (updateData.getTechnician() != null) {
                    existing.setTechnician(updateData.getTechnician().trim());
                }
                if (updateData.getFaultDescription() != null && !updateData.getFaultDescription().trim().isEmpty()) {
                    existing.setFaultDescription(updateData.getFaultDescription().trim());
                }
                if (updateData.getScheduledDate() != null) {
                    existing.setScheduledDate(updateData.getScheduledDate());
                }

                existing.setStatus(newStatus);

                // If completing maintenance:
                if ("COMPLETED".equalsIgnoreCase(newStatus)) {
                    Date compDate = updateData.getCompletedDate() != null ? updateData.getCompletedDate() : Date.valueOf(LocalDate.now());
                    existing.setCompletedDate(compDate);

                    // Atomically return asset to AVAILABLE
                    int assetUpdated = assetDAO.changeStatus(con, existing.getAssetId(), "UNDER_MAINTENANCE", "AVAILABLE");
                    if (assetUpdated == 0) {
                        // Could already be AVAILABLE if manually changed, but log
                        LOGGER.warning("Asset " + existing.getAssetId() + " was not UNDER_MAINTENANCE when completing ticket " + mid);
                    }
                }

                maintenanceDAO.update(con, existing);
                con.commit();
                LOGGER.info("Maintenance updated: " + mid + " to status " + newStatus);
                return maintenanceDAO.findById(mid).orElse(existing);

            } catch (Exception e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed during maintenance update", rollbackEx);
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Transaction error during maintenance update: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public Maintenance getMaintenanceById(User sessionUser, String maintenanceId) throws SQLException {
        checkAuthorizedStaff(sessionUser);
        if (maintenanceId == null || maintenanceId.trim().isEmpty()) {
            throw new MaintenanceValidationException("Maintenance ID is required");
        }
        return maintenanceDAO.findById(maintenanceId.trim())
                .orElseThrow(() -> new MaintenanceNotFoundException("Maintenance ticket not found: " + maintenanceId));
    }

    @Override
    public PagedResult<Maintenance> searchMaintenance(User sessionUser, String status, String assetId,
                                                      String technician, int page, int size) throws SQLException {
        checkAuthorizedStaff(sessionUser);
        return maintenanceDAO.search(status, assetId, technician, page, size);
    }

    @Override
    public BigDecimal getTotalCost(User sessionUser, String status, String assetId, String technician) throws SQLException {
        checkAuthorizedStaff(sessionUser);
        return maintenanceDAO.getTotalCost(status, assetId, technician);
    }
}
