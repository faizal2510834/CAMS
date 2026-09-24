package com.cams.service;

import com.cams.model.PagedResult;
import com.cams.model.Purchase;

import java.sql.SQLException;

/**
 * Service interface for Purchase procurement lifecycle and approval workflow.
 */
public interface PurchaseService {

    Purchase getPurchaseById(String purchaseId) throws SQLException;

    PagedResult<Purchase> getPurchases(String status, String vendorId, int page, int size) throws SQLException;

    Purchase createPurchase(Purchase purchase) throws SQLException;

    Purchase approvePurchase(String purchaseId, String approvedBy) throws SQLException;

    Purchase rejectPurchase(String purchaseId, String reason) throws SQLException;
}
