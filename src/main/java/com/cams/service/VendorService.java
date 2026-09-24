package com.cams.service;

import com.cams.model.PagedResult;
import com.cams.model.Vendor;

import java.sql.SQLException;
import java.util.List;

/**
 * Service interface for Vendor business logic and validation.
 */
public interface VendorService {

    Vendor getVendorById(String vendorId) throws SQLException;

    PagedResult<Vendor> getVendors(String search, String active, int page, int size) throws SQLException;

    List<Vendor> getActiveVendors() throws SQLException;

    Vendor createVendor(Vendor vendor) throws SQLException;

    Vendor updateVendor(String vendorId, Vendor vendor) throws SQLException;

    void deactivateVendor(String vendorId) throws SQLException;
}
