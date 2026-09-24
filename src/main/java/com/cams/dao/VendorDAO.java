package com.cams.dao;

import com.cams.model.PagedResult;
import com.cams.model.Vendor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for VENDORS table operations.
 */
public interface VendorDAO {

    Optional<Vendor> findById(String vendorId) throws SQLException;

    Optional<Vendor> findById(Connection conn, String vendorId) throws SQLException;

    boolean existsById(String vendorId) throws SQLException;

    boolean existsById(Connection conn, String vendorId) throws SQLException;

    boolean insert(Connection conn, Vendor vendor) throws SQLException;

    boolean update(Connection conn, Vendor vendor) throws SQLException;

    boolean deactivate(Connection conn, String vendorId) throws SQLException;

    PagedResult<Vendor> search(String search, String active, int page, int size) throws SQLException;

    List<Vendor> findActiveVendors() throws SQLException;
}
