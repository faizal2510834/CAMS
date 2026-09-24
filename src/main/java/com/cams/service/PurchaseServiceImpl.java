package com.cams.service;

import com.cams.dao.AssetDAO;
import com.cams.dao.AssetDAOImpl;
import com.cams.dao.PurchaseDAO;
import com.cams.dao.PurchaseDAOImpl;
import com.cams.dao.VendorDAO;
import com.cams.dao.VendorDAOImpl;
import com.cams.model.Asset;
import com.cams.model.PagedResult;
import com.cams.model.Purchase;
import com.cams.model.Vendor;
import com.cams.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of PurchaseService enforcing procurement business rules and atomic transactions.
 */
public class PurchaseServiceImpl implements PurchaseService {

    private static final Logger LOGGER = Logger.getLogger(PurchaseServiceImpl.class.getName());

    private final PurchaseDAO purchaseDAO;
    private final VendorDAO vendorDAO;
    private final AssetDAO assetDAO;

    public PurchaseServiceImpl() {
        this(new PurchaseDAOImpl(), new VendorDAOImpl(), new AssetDAOImpl());
    }

    public PurchaseServiceImpl(PurchaseDAO purchaseDAO, VendorDAO vendorDAO, AssetDAO assetDAO) {
        this.purchaseDAO = purchaseDAO;
        this.vendorDAO = vendorDAO;
        this.assetDAO = assetDAO;
    }

    @Override
    public Purchase getPurchaseById(String purchaseId) throws SQLException {
        if (purchaseId == null || purchaseId.trim().isEmpty()) {
            throw new PurchaseValidationException("Purchase ID is required");
        }
        return purchaseDAO.findById(purchaseId.trim())
                .orElseThrow(() -> new PurchaseNotFoundException("Purchase record not found with ID: " + purchaseId));
    }

    @Override
    public PagedResult<Purchase> getPurchases(String status, String vendorId, int page, int size) throws SQLException {
        int validPage = Math.max(1, page);
        int validSize = (size <= 0 || size > 100) ? 10 : size;
        return purchaseDAO.search(status, vendorId, validPage, validSize);
    }

    @Override
    public Purchase createPurchase(Purchase purchase) throws SQLException {
        if (purchase == null) {
            throw new PurchaseValidationException("Purchase details cannot be empty");
        }

        // 1. Asset ID Validation
        if (purchase.getAssetId() == null || purchase.getAssetId().trim().isEmpty()) {
            throw new PurchaseValidationException("Asset ID is required");
        }
        String assetId = purchase.getAssetId().trim();
        purchase.setAssetId(assetId);

        Asset asset = assetDAO.findById(assetId)
                .orElseThrow(() -> new PurchaseNotFoundException("Asset not found with ID: " + assetId));

        if ("DISPOSED".equalsIgnoreCase(asset.getStatus())) {
            throw new PurchaseConflictException("Cannot record purchase against a retired/disposed asset: " + assetId);
        }

        if (purchaseDAO.existsByAssetId(assetId)) {
            throw new PurchaseConflictException("Asset '" + assetId + "' already has an associated purchase record.");
        }

        // 2. Vendor Validation
        if (purchase.getVendorId() == null || purchase.getVendorId().trim().isEmpty()) {
            throw new PurchaseValidationException("Vendor ID is required");
        }
        String vendorId = purchase.getVendorId().trim();
        purchase.setVendorId(vendorId);

        Vendor vendor = vendorDAO.findById(vendorId)
                .orElseThrow(() -> new PurchaseNotFoundException("Vendor not found with ID: " + vendorId));

        if (!vendor.isActive()) {
            throw new PurchaseValidationException("Vendor '" + vendorId + "' is inactive and cannot be selected for new purchases.");
        }

        // 3. Invoice Number Validation
        if (purchase.getInvoiceNumber() == null || purchase.getInvoiceNumber().trim().isEmpty()) {
            throw new PurchaseValidationException("Invoice number is required");
        }
        purchase.setInvoiceNumber(purchase.getInvoiceNumber().trim());
        if (purchase.getInvoiceNumber().length() > 100) {
            throw new PurchaseValidationException("Invoice number must not exceed 100 characters");
        }

        // 4. Cost Validation
        if (purchase.getCost() == null || purchase.getCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PurchaseValidationException("Purchase cost must be greater than zero");
        }

        // 5. Purchase Date Validation (cannot be in the future)
        if (purchase.getPurchaseDate() == null) {
            throw new PurchaseValidationException("Purchase date is required");
        }
        LocalDate date = purchase.getPurchaseDate().toLocalDate();
        if (date.isAfter(LocalDate.now())) {
            throw new PurchaseValidationException("Purchase date cannot be in the future");
        }

        // 6. ID & Status Defaults
        String purchaseId = purchase.getPurchaseId();
        if (purchaseId == null || purchaseId.trim().isEmpty()) {
            purchaseId = "PUR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            purchase.setPurchaseId(purchaseId);
        } else {
            purchase.setPurchaseId(purchaseId.trim());
        }

        purchase.setStatus("PENDING");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                purchaseDAO.insert(conn, purchase);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                // Check for ORA-00001 (Unique constraint on asset_id or purchase_id)
                if (e.getErrorCode() == 1 || (e.getMessage() != null && e.getMessage().contains("unique constraint"))) {
                    throw new PurchaseConflictException("Asset '" + assetId + "' already has an associated purchase record.");
                }
                throw e;
            }
        }

        return getPurchaseById(purchase.getPurchaseId());
    }

    @Override
    public Purchase approvePurchase(String purchaseId, String approvedBy) throws SQLException {
        if (purchaseId == null || purchaseId.trim().isEmpty()) {
            throw new PurchaseValidationException("Purchase ID is required for approval");
        }
        String pid = purchaseId.trim();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Purchase purchase = purchaseDAO.findById(conn, pid)
                        .orElseThrow(() -> new PurchaseNotFoundException("Purchase not found with ID: " + pid));

                if (!"PENDING".equalsIgnoreCase(purchase.getStatus())) {
                    throw new PurchaseConflictException("Purchase is already " + purchase.getStatus());
                }

                Timestamp now = new Timestamp(System.currentTimeMillis());
                String approver = (approvedBy != null && !approvedBy.trim().isEmpty()) ? approvedBy.trim() : "Administrator";

                // Step 1: Update purchase status to APPROVED
                purchaseDAO.updateStatus(conn, pid, "APPROVED", approver, now);

                // Step 2: In the SAME transaction, link vendor to asset
                boolean assetUpdated = assetDAO.updateVendorId(conn, purchase.getAssetId(), purchase.getVendorId());
                if (!assetUpdated) {
                    throw new SQLException("Failed to link vendor '" + purchase.getVendorId() + "' to asset '" + purchase.getAssetId() + "'");
                }

                // Step 3: Commit atomically
                conn.commit();
                LOGGER.info("Atomically approved purchase " + pid + " and linked vendor " + purchase.getVendorId() + " to asset " + purchase.getAssetId());

            } catch (Exception e) {
                conn.rollback();
                if (e instanceof PurchaseConflictException pce) {
                    throw pce;
                }
                if (e instanceof PurchaseNotFoundException pnfe) {
                    throw pnfe;
                }
                if (e instanceof PurchaseValidationException pve) {
                    throw pve;
                }
                if (e instanceof SQLException se) {
                    throw se;
                }
                throw new SQLException("Error approving purchase: " + e.getMessage(), e);
            }
        }

        return getPurchaseById(pid);
    }

    @Override
    public Purchase rejectPurchase(String purchaseId, String reason) throws SQLException {
        if (purchaseId == null || purchaseId.trim().isEmpty()) {
            throw new PurchaseValidationException("Purchase ID is required for rejection");
        }
        String pid = purchaseId.trim();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Purchase purchase = purchaseDAO.findById(conn, pid)
                        .orElseThrow(() -> new PurchaseNotFoundException("Purchase not found with ID: " + pid));

                if (!"PENDING".equalsIgnoreCase(purchase.getStatus())) {
                    throw new PurchaseConflictException("Purchase is already " + purchase.getStatus());
                }

                Timestamp now = new Timestamp(System.currentTimeMillis());

                // Reject purchase status; asset's vendor remains untouched
                purchaseDAO.updateStatus(conn, pid, "REJECTED", null, now);
                conn.commit();
                LOGGER.info("Purchase " + pid + " status updated to REJECTED");

            } catch (Exception e) {
                conn.rollback();
                if (e instanceof PurchaseConflictException pce) {
                    throw pce;
                }
                if (e instanceof PurchaseNotFoundException pnfe) {
                    throw pnfe;
                }
                if (e instanceof PurchaseValidationException pve) {
                    throw pve;
                }
                if (e instanceof SQLException se) {
                    throw se;
                }
                throw new SQLException("Error rejecting purchase: " + e.getMessage(), e);
            }
        }

        return getPurchaseById(pid);
    }
}
