package com.cams.dao;

import com.cams.model.ClassroomDetails;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * DAO interface for Classroom Asset category-specific asset details.
 */
public interface ClassroomDetailsDAO {

    /**
     * Inserts or updates classroom details for the specified assetId using the provided Connection.
     */
    boolean insertOrUpdate(Connection conn, String assetId, ClassroomDetails details) throws SQLException;

    /**
     * Retrieves classroom details for the specified assetId using the provided Connection.
     */
    Optional<ClassroomDetails> findByAssetId(Connection conn, String assetId) throws SQLException;
}
