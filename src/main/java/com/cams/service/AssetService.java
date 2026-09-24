package com.cams.service;

import com.cams.model.Asset;
import com.cams.model.AssetQueryCriteria;
import com.cams.model.PagedResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Asset Management operations and business rule enforcement.
 */
public interface AssetService {

    /**
     * Retrieves an asset by its unique Asset ID.
     */
    Asset getAssetById(String assetId) throws AssetNotFoundException, SQLException;

    /**
     * Queries assets with pagination, filtering, and whitelisted sorting.
     */
    PagedResult<Asset> getAssets(AssetQueryCriteria criteria) throws SQLException;

    /**
     * Registers a new asset in the system.
     * Enforces ID format, cost > 0, duplicate checks, warranty >= purchase date, and dropdown validations.
     */
    Asset addAsset(Asset asset) throws AssetValidationException, AssetConflictException, SQLException;

    /**
     * Updates an asset's editable fields.
     * Disallows editing of DISPOSED assets and immutability of asset_id and status.
     */
    Asset updateAsset(String assetId, Asset updateData)
            throws AssetValidationException, AssetConflictException, AssetNotFoundException, SQLException;

    /**
     * Soft-deletes/retires an asset with a specified reason.
     * Fails if the asset is currently ISSUED or already DISPOSED.
     */
    void retireAsset(String assetId, String reason)
            throws AssetConflictException, AssetNotFoundException, SQLException;

    /**
     * Atomically changes status within a provided database Connection transaction.
     * Validates state transition lifecycle and throws AssetConflictException if 0 rows updated.
     */
    void changeStatus(Connection con, String assetId, String expectedStatus, String newStatus)
            throws AssetConflictException, SQLException;

    /**
     * Returns the master list of allowed options for Departments, Categories, and Locations.
     */
    Map<String, List<String>> getOptions();
}
