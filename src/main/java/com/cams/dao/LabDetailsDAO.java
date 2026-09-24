package com.cams.dao;

import com.cams.model.LabDetails;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO interface for Laboratory Equipment category-specific asset details.
 */
public interface LabDetailsDAO {

    /**
     * Inserts or updates lab details for the specified assetId using the provided Connection.
     */
    boolean insertOrUpdate(Connection conn, String assetId, LabDetails details) throws SQLException;

    /**
     * Retrieves lab details for the specified assetId using the provided Connection.
     */
    Optional<LabDetails> findByAssetId(Connection conn, String assetId) throws SQLException;
}
