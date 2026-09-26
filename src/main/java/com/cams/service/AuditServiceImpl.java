package com.cams.service;

import com.cams.dao.AssetDAO;
import com.cams.dao.AssetDAOImpl;
import com.cams.dao.AuditDAO;
import com.cams.dao.AuditDAOImpl;
import com.cams.model.Asset;
import com.cams.model.AuditQueryCriteria;
import com.cams.model.AuditRecord;
import com.cams.model.AuditSummary;
import com.cams.model.PagedResult;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service implementation for Physical Inventory Audits.
 */
public class AuditServiceImpl implements AuditService {

    private static final Logger LOGGER = Logger.getLogger(AuditServiceImpl.class.getName());
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("VERIFIED", "MISSING", "MISLOCATED"));

    private final AuditDAO auditDAO;
    private final AssetDAO assetDAO;

    public AuditServiceImpl() {
        this(new AuditDAOImpl(), new AssetDAOImpl());
    }

    public AuditServiceImpl(AuditDAO auditDAO, AssetDAO assetDAO) {
        this.auditDAO = auditDAO;
        this.assetDAO = assetDAO;
    }

    @Override
    public AuditRecord recordAudit(String assetId, String status, String remarks, String verifiedBy)
            throws SQLException, AssetNotFoundException, AssetValidationException {

        if (assetId == null || assetId.trim().isEmpty()) {
            throw new AssetValidationException("Asset ID is required for verification");
        }
        String cleanAssetId = assetId.trim();

        if (status == null || status.trim().isEmpty()) {
            throw new AssetValidationException("Audit verification status is required");
        }
        String cleanStatus = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(cleanStatus)) {
            throw new AssetValidationException("Invalid audit status '" + status + "'. Must be VERIFIED, MISSING, or MISLOCATED");
        }

        if (verifiedBy == null || verifiedBy.trim().isEmpty()) {
            throw new AssetValidationException("Auditor username is required");
        }

        // Verify that target asset exists in inventory
        Optional<Asset> assetOpt = assetDAO.findById(cleanAssetId);
        if (!assetOpt.isPresent()) {
            throw new AssetNotFoundException("Asset '" + cleanAssetId + "' not found in inventory registry");
        }
        Asset asset = assetOpt.get();

        String cleanRemarks = null;
        if (remarks != null && !remarks.trim().isEmpty()) {
            cleanRemarks = remarks.trim();
            if (cleanRemarks.length() > 255) {
                cleanRemarks = cleanRemarks.substring(0, 255);
            }
        }

        String auditId = "AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        AuditRecord record = new AuditRecord();
        record.setAuditId(auditId);
        record.setAssetId(cleanAssetId);
        record.setVerifiedBy(verifiedBy.trim());
        record.setStatus(cleanStatus);
        record.setRemarks(cleanRemarks);

        boolean inserted = auditDAO.insert(record);
        if (!inserted) {
            throw new SQLException("Failed to record inventory audit in database");
        }

        // Return enriched record
        Optional<AuditRecord> saved = auditDAO.findById(auditId);
        if (saved.isPresent()) {
            return saved.get();
        }

        record.setAssetName(asset.getAssetName());
        record.setCategory(asset.getCategory());
        record.setDepartment(asset.getDepartment());
        record.setCurrentLocation(asset.getLocation());
        record.setAssetStatus(asset.getStatus());
        return record;
    }

    @Override
    public PagedResult<AuditRecord> getAudits(AuditQueryCriteria criteria) throws SQLException {
        if (criteria == null) {
            criteria = new AuditQueryCriteria();
        }
        return auditDAO.search(criteria);
    }

    @Override
    public AuditSummary getAuditSummary() throws SQLException {
        return auditDAO.getSummary();
    }
}
