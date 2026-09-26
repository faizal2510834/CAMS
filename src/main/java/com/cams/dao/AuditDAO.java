package com.cams.dao;

import com.cams.model.AuditQueryCriteria;
import com.cams.model.AuditRecord;
import com.cams.model.AuditSummary;
import com.cams.model.PagedResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Data Access Object for INVENTORY_AUDITS operations.
 */
public interface AuditDAO {

    /**
     * Inserts a new audit verification record using an external Connection.
     */
    boolean insert(Connection conn, AuditRecord record) throws SQLException;

    /**
     * Inserts a new audit verification record.
     */
    boolean insert(AuditRecord record) throws SQLException;

    /**
     * Finds an audit record by its primary key ID.
     */
    Optional<AuditRecord> findById(String auditId) throws SQLException;

    /**
     * Searches audit history with filters, sorting, and pagination.
     */
    PagedResult<AuditRecord> search(AuditQueryCriteria criteria) throws SQLException;

    /**
     * Retrieves aggregated status counts (Total, Verified, Missing, Mislocated).
     */
    AuditSummary getSummary() throws SQLException;
}
