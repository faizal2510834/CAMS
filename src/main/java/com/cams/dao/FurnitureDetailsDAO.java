package com.cams.dao;

import com.cams.model.FurnitureDetails;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO interface for Furniture category-specific asset details.
 */
public interface FurnitureDetailsDAO {

    /**
     * Inserts or updates furniture details for the specified assetId using the provided Connection.
     */
    boolean insertOrUpdate(Connection conn, String assetId, FurnitureDetails details) throws SQLException;

    /**
     * Retrieves furniture details for the specified assetId using the provided Connection.
     */
    Optional<FurnitureDetails> findByAssetId(Connection conn, String assetId) throws SQLException;
}
