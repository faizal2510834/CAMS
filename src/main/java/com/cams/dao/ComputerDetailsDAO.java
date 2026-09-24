package com.cams.dao;

import com.cams.model.ComputerDetails;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO interface for Computer category-specific asset details.
 */
public interface ComputerDetailsDAO {

    /**
     * Inserts or updates computer details for the specified assetId using the provided Connection.
     */
    boolean insertOrUpdate(Connection conn, String assetId, ComputerDetails details) throws SQLException;

    /**
     * Retrieves computer details for the specified assetId using the provided Connection.
     */
    Optional<ComputerDetails> findByAssetId(Connection conn, String assetId) throws SQLException;
}
