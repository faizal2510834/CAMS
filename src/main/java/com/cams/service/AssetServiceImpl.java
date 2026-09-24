package com.cams.service;

import com.cams.dao.AssetDAO;
import com.cams.dao.AssetDAOImpl;
import com.cams.model.Asset;
import com.cams.model.AssetQueryCriteria;
import com.cams.model.PagedResult;
import com.cams.util.AssetConstants;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implementation of AssetService enforcing business rules, status lifecycle transitions,
 * format validation, and duplicate checking.
 */
public class AssetServiceImpl implements AssetService {

    private static final Pattern ASSET_ID_PATTERN = Pattern.compile("^[A-Z0-9-]+$");

    // Allowed status transitions
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "AVAILABLE", Set.of("ISSUED", "UNDER_MAINTENANCE", "DISPOSED"),
            "ISSUED", Set.of("AVAILABLE", "UNDER_MAINTENANCE"),
            "UNDER_MAINTENANCE", Set.of("AVAILABLE", "DISPOSED"),
            "DISPOSED", Set.of() // Terminal
    );

    private final AssetDAO assetDAO;

    public AssetServiceImpl() {
        this(new AssetDAOImpl());
    }

    public AssetServiceImpl(AssetDAO assetDAO) {
        this.assetDAO = assetDAO;
    }

    @Override
    public Asset getAssetById(String assetId) throws AssetNotFoundException, SQLException {
        if (assetId == null || assetId.trim().isEmpty()) {
            throw new AssetNotFoundException("Asset ID cannot be empty");
        }
        return assetDAO.findById(assetId.trim())
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with ID: " + assetId.trim()));
    }

    @Override
    public PagedResult<Asset> getAssets(AssetQueryCriteria criteria) throws SQLException {
        if (criteria == null) {
            criteria = new AssetQueryCriteria();
        }
        return assetDAO.search(criteria);
    }

    @Override
    public Asset addAsset(Asset asset) throws AssetValidationException, AssetConflictException, SQLException {
        if (asset == null) {
            throw new AssetValidationException("Asset payload cannot be null");
        }

        // 1. Validate ASSET_ID format: ^[A-Z0-9-]+$
        if (asset.getAssetId() == null || asset.getAssetId().trim().isEmpty()) {
            throw new AssetValidationException("Asset ID is required");
        }
        String normalizedId = asset.getAssetId().trim().toUpperCase();
        if (!ASSET_ID_PATTERN.matcher(normalizedId).matches()) {
            throw new AssetValidationException("Asset ID '" + normalizedId + "' is invalid. Must match pattern ^[A-Z0-9-]+$");
        }
        asset.setAssetId(normalizedId);

        // 2. Reject duplicate at Service level
        if (assetDAO.findById(normalizedId).isPresent()) {
            throw new AssetConflictException("Duplicate Asset ID: Asset '" + normalizedId + "' already exists");
        }

        // 3. Validate mandatory fields
        if (asset.getAssetName() == null || asset.getAssetName().trim().isEmpty()) {
            throw new AssetValidationException("Asset Name is required");
        }
        asset.setAssetName(asset.getAssetName().trim());

        if (!AssetConstants.isValidCategory(asset.getCategory())) {
            throw new AssetValidationException("Invalid Category: '" + asset.getCategory() + "'. Allowed: " + AssetConstants.CATEGORIES);
        }

        if (!AssetConstants.isValidDepartment(asset.getDepartment())) {
            throw new AssetValidationException("Invalid Department: '" + asset.getDepartment() + "'. Allowed: " + AssetConstants.DEPARTMENTS);
        }

        if (!AssetConstants.isValidLocation(asset.getLocation())) {
            throw new AssetValidationException("Invalid Location: '" + asset.getLocation() + "'. Allowed: " + AssetConstants.LOCATIONS);
        }

        if (asset.getPurchaseDate() == null) {
            throw new AssetValidationException("Purchase date is required");
        }

        // 4. PURCHASE_COST must be > 0 (Cost 0 and negative return 400)
        if (asset.getPurchaseCost() == null || asset.getPurchaseCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AssetValidationException("Purchase cost must be greater than zero");
        }

        // 5. WARRANTY_EXPIRY must be >= PURCHASE_DATE if provided
        if (asset.getWarrantyExpiry() != null && asset.getWarrantyExpiry().before(asset.getPurchaseDate())) {
            throw new AssetValidationException("Warranty expiry date cannot be earlier than purchase date");
        }

        // 6. Default status to AVAILABLE
        asset.setStatus("AVAILABLE");

        // 7. Persist to DB (DB unique constraint handles race conditions)
        try {
            boolean created = assetDAO.create(asset);
            if (!created) {
                throw new SQLException("Failed to insert asset record");
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("ORA-00001")) {
                throw new AssetConflictException("Duplicate Asset ID: Asset '" + normalizedId + "' already exists in database");
            }
            throw e;
        }

        try {
            return getAssetById(normalizedId);
        } catch (AssetNotFoundException e) {
            return asset;
        }
    }

    @Override
    public Asset updateAsset(String assetId, Asset updateData)
            throws AssetValidationException, AssetConflictException, AssetNotFoundException, SQLException {

        if (assetId == null || assetId.trim().isEmpty()) {
            throw new AssetValidationException("Asset ID is required for update");
        }
        if (updateData == null) {
            throw new AssetValidationException("Update data cannot be null");
        }

        Asset existing = getAssetById(assetId.trim());

        // Editing a DISPOSED asset returns 409 Conflict
        if ("DISPOSED".equalsIgnoreCase(existing.getStatus())) {
            throw new AssetConflictException("Cannot edit asset '" + assetId.trim() + "' because it is DISPOSED (retired)");
        }

        // Validate editable fields
        if (updateData.getAssetName() == null || updateData.getAssetName().trim().isEmpty()) {
            throw new AssetValidationException("Asset Name is required");
        }
        if (!AssetConstants.isValidCategory(updateData.getCategory())) {
            throw new AssetValidationException("Invalid Category: '" + updateData.getCategory() + "'. Allowed: " + AssetConstants.CATEGORIES);
        }
        if (!AssetConstants.isValidDepartment(updateData.getDepartment())) {
            throw new AssetValidationException("Invalid Department: '" + updateData.getDepartment() + "'. Allowed: " + AssetConstants.DEPARTMENTS);
        }
        if (!AssetConstants.isValidLocation(updateData.getLocation())) {
            throw new AssetValidationException("Invalid Location: '" + updateData.getLocation() + "'. Allowed: " + AssetConstants.LOCATIONS);
        }
        if (updateData.getPurchaseDate() == null) {
            throw new AssetValidationException("Purchase date is required");
        }
        if (updateData.getPurchaseCost() == null || updateData.getPurchaseCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AssetValidationException("Purchase cost must be greater than zero");
        }
        if (updateData.getWarrantyExpiry() != null && updateData.getWarrantyExpiry().before(updateData.getPurchaseDate())) {
            throw new AssetValidationException("Warranty expiry date cannot be earlier than purchase date");
        }

        // asset_id and status are NOT changeable via PUT
        updateData.setAssetId(existing.getAssetId());
        updateData.setStatus(existing.getStatus());

        boolean updated = assetDAO.update(updateData);
        if (!updated) {
            throw new SQLException("Failed to update asset: " + assetId);
        }

        return getAssetById(existing.getAssetId());
    }

    @Override
    public void retireAsset(String assetId, String reason)
            throws AssetConflictException, AssetNotFoundException, SQLException {

        Asset existing = getAssetById(assetId);

        // Retiring an ISSUED asset returns 409
        if ("ISSUED".equalsIgnoreCase(existing.getStatus())) {
            throw new AssetConflictException("Cannot retire asset '" + assetId + "': Asset is currently ISSUED. Return it to inventory first.");
        }

        // Retiring an already-DISPOSED asset returns 409
        if ("DISPOSED".equalsIgnoreCase(existing.getStatus())) {
            throw new AssetConflictException("Asset '" + assetId + "' is already retired/disposed");
        }

        Date today = new Date(System.currentTimeMillis());
        String finalReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : "Retired from operational inventory";

        boolean retired = assetDAO.retire(assetId.trim(), today, finalReason);
        if (!retired) {
            throw new SQLException("Failed to retire asset: " + assetId);
        }
    }

    @Override
    public void changeStatus(Connection con, String assetId, String expectedStatus, String newStatus)
            throws AssetConflictException, SQLException {

        if (assetId == null || expectedStatus == null || newStatus == null) {
            throw new IllegalArgumentException("Asset ID, expected status, and new status must not be null");
        }

        String from = expectedStatus.trim().toUpperCase();
        String to = newStatus.trim().toUpperCase();

        if (!AssetConstants.isValidStatus(to)) {
            throw new AssetConflictException("Target status '" + to + "' is not a recognized asset status");
        }

        // Validate allowed state transitions
        Set<String> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new AssetConflictException("Invalid status transition: Cannot transition asset from '" + from + "' to '" + to + "'");
        }

        int affected = assetDAO.changeStatus(con, assetId.trim(), from, to);
        if (affected == 0) {
            throw new AssetConflictException("Status transition conflict: Asset '" + assetId.trim() +
                    "' was expected to be in status '" + from + "', but 0 rows were updated. Current status has changed.");
        }
    }

    @Override
    public Map<String, List<String>> getOptions() {
        return AssetConstants.getOptions();
    }
}
