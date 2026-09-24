package com.cams.dao;

import com.cams.model.Asset;
import com.cams.model.AssetQueryCriteria;
import com.cams.model.PagedResult;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object for ASSETS operations.
 */
public interface AssetDAO {

    /**
     * Finds an asset by its primary key ID.
     */
    Optional<Asset> findById(String assetId) throws SQLException;

    /**
     * Persists a new asset record in the ASSETS table.
     */
    boolean create(Asset asset) throws SQLException;

    /**
     * Updates an asset's editable fields.
     * Note: ASSET_ID and STATUS cannot be changed through this method.
     * UPDATED_AT is refreshed to CURRENT_TIMESTAMP.
     */
    boolean update(Asset asset) throws SQLException;

    /**
     * Soft-deletes/retires an asset by transitioning its STATUS to 'DISPOSED'
     * and recording the disposal date and reason.
     */
    boolean retire(String assetId, Date disposedDate, String reason) throws SQLException;

    /**
     * Atomically transitions an asset's status using an external transaction Connection.
     * UPDATE ASSETS SET STATUS=?, UPDATED_AT=CURRENT_TIMESTAMP WHERE ASSET_ID=? AND STATUS=?
     *
     * @return count of updated rows (1 if successful, 0 if expectedStatus didn't match)
     */
    int changeStatus(Connection con, String assetId, String expectedStatus, String newStatus) throws SQLException;

    /**
     * Executes a dynamic, parameterized search with pagination and whitelisted sorting.
     */
    PagedResult<Asset> search(AssetQueryCriteria criteria) throws SQLException;
}
