package com.cams.dao;

import com.cams.model.Maintenance;
import com.cams.model.PagedResult;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object for the MAINTENANCE table.
 */
public interface MaintenanceDAO {

    /**
     * Inserts a new maintenance ticket within an existing database transaction.
     */
    int create(Connection con, Maintenance maintenance) throws SQLException;

    /**
     * Inserts a new maintenance ticket using a standalone connection.
     */
    int create(Maintenance maintenance) throws SQLException;

    /**
     * Updates an existing maintenance ticket within an existing database transaction.
     */
    int update(Connection con, Maintenance maintenance) throws SQLException;

    /**
     * Updates an existing maintenance ticket using a standalone connection.
     */
    int update(Maintenance maintenance) throws SQLException;

    /**
     * Finds a maintenance ticket by ID using an existing connection.
     */
    Optional<Maintenance> findById(Connection con, String maintenanceId) throws SQLException;

    /**
     * Finds a maintenance ticket by ID using a standalone connection.
     */
    Optional<Maintenance> findById(String maintenanceId) throws SQLException;

    /**
     * Checks if there is an active maintenance ticket (SCHEDULED, IN_PROGRESS, REQUIRES_FURTHER_REPAIR)
     * for a given asset ID within a transaction.
     */
    Optional<Maintenance> findActiveByAssetId(Connection con, String assetId) throws SQLException;

    /**
     * Checks if there is an active maintenance ticket for a given asset ID.
     */
    Optional<Maintenance> findActiveByAssetId(String assetId) throws SQLException;

    /**
     * Paginated search for maintenance tickets with optional filters.
     */
    PagedResult<Maintenance> search(String status, String assetId, String technician, int page, int size) throws SQLException;

    /**
     * Computes total cost across filtered maintenance tickets.
     */
    BigDecimal getTotalCost(String status, String assetId, String technician) throws SQLException;
}
