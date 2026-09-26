package com.cams.service;

import com.cams.model.AuditQueryCriteria;
import com.cams.model.AuditRecord;
import com.cams.model.AuditSummary;
import com.cams.model.PagedResult;

import java.sql.SQLException;

/**
 * Service contract for Physical Inventory Audits (Module 8).
 */
public interface AuditService {

    /**
     * Conducts and records an asset verification.
     */
    AuditRecord recordAudit(String assetId, String status, String remarks, String verifiedBy)
            throws SQLException, AssetNotFoundException, AssetValidationException;

    /**
     * Retrieves audit history filtered by asset, status, department, and date range.
     */
    PagedResult<AuditRecord> getAudits(AuditQueryCriteria criteria) throws SQLException;

    /**
     * Returns aggregate count of audits across statuses.
     */
    AuditSummary getAuditSummary() throws SQLException;
}
