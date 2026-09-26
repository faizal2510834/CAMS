package com.cams.service;

import com.cams.model.Maintenance;
import com.cams.model.PagedResult;
import com.cams.model.User;

import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Service interface for Asset Maintenance Management.
 */
public interface MaintenanceService {

    /**
     * Manually schedules a new maintenance ticket on an asset.
     * Transitions an AVAILABLE asset to UNDER_MAINTENANCE.
     */
    Maintenance scheduleMaintenance(User sessionUser, Maintenance maintenance) throws SQLException;

    /**
     * Updates an existing maintenance ticket (status, technician, cost, remarks).
     * If status transitions to COMPLETED, flips asset back to AVAILABLE.
     */
    Maintenance updateMaintenance(User sessionUser, String maintenanceId, Maintenance updateData) throws SQLException;

    /**
     * Retrieves maintenance ticket by ID.
     */
    Maintenance getMaintenanceById(User sessionUser, String maintenanceId) throws SQLException;

    /**
     * Searches maintenance tickets with filters and pagination.
     */
    PagedResult<Maintenance> searchMaintenance(User sessionUser, String status, String assetId,
                                               String technician, int page, int size) throws SQLException;

    /**
     * Returns total maintenance cost for Administrator view.
     */
    BigDecimal getTotalCost(User sessionUser, String status, String assetId, String technician) throws SQLException;
}
