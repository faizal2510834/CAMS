package com.cams.dao;

import com.cams.model.AssetIssue;
import com.cams.model.PagedResult;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object for ASSET_ISSUES table operations.
 */
public interface IssueDAO {

    boolean insert(Connection conn, AssetIssue issue) throws SQLException;

    Optional<AssetIssue> findById(String issueId) throws SQLException;

    Optional<AssetIssue> findById(Connection conn, String issueId) throws SQLException;

    int markReturned(Connection conn, String issueId, Date actualReturnDate,
                     String conditionOnReturn, String returnRemarks, String returnedTo) throws SQLException;

    PagedResult<AssetIssue> search(String status, String assetId, Long userId, String deptId, int page, int size) throws SQLException;
}
