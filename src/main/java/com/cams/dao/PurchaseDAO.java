package com.cams.dao;

import com.cams.model.PagedResult;
import com.cams.model.Purchase;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Data Access Object for PURCHASES table operations.
 */
public interface PurchaseDAO {

    Optional<Purchase> findById(String purchaseId) throws SQLException;

    Optional<Purchase> findById(Connection conn, String purchaseId) throws SQLException;

    Optional<Purchase> findByAssetId(String assetId) throws SQLException;

    Optional<Purchase> findByAssetId(Connection conn, String assetId) throws SQLException;

    boolean existsByAssetId(String assetId) throws SQLException;

    boolean existsByAssetId(Connection conn, String assetId) throws SQLException;

    boolean insert(Connection conn, Purchase purchase) throws SQLException;

    boolean updateStatus(Connection conn, String purchaseId, String status, String approvedBy, Timestamp approvedAt) throws SQLException;

    PagedResult<Purchase> search(String status, String vendorId, int page, int size) throws SQLException;
}
