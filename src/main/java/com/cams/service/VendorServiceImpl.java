package com.cams.service;

import com.cams.dao.VendorDAO;
import com.cams.dao.VendorDAOImpl;
import com.cams.model.PagedResult;
import com.cams.model.Vendor;
import com.cams.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implementation of VendorService managing business validation and persistence.
 */
public class VendorServiceImpl implements VendorService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CONTACT_PATTERN = Pattern.compile("^[0-9+\\-\\s()]{7,20}$");

    private final VendorDAO vendorDAO;

    public VendorServiceImpl() {
        this(new VendorDAOImpl());
    }

    public VendorServiceImpl(VendorDAO vendorDAO) {
        this.vendorDAO = vendorDAO;
    }

    @Override
    public Vendor getVendorById(String vendorId) throws SQLException {
        if (vendorId == null || vendorId.trim().isEmpty()) {
            throw new VendorValidationException("Vendor ID is required");
        }
        return vendorDAO.findById(vendorId.trim())
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found with ID: " + vendorId));
    }

    @Override
    public PagedResult<Vendor> getVendors(String search, String active, int page, int size) throws SQLException {
        int validPage = Math.max(1, page);
        int validSize = (size <= 0 || size > 100) ? 10 : size;
        return vendorDAO.search(search, active, validPage, validSize);
    }

    @Override
    public List<Vendor> getActiveVendors() throws SQLException {
        return vendorDAO.findActiveVendors();
    }

    @Override
    public Vendor createVendor(Vendor vendor) throws SQLException {
        if (vendor == null) {
            throw new VendorValidationException("Vendor data cannot be empty");
        }

        validateVendor(vendor);

        String vendorId = vendor.getVendorId();
        if (vendorId == null || vendorId.trim().isEmpty()) {
            vendorId = "VND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            vendor.setVendorId(vendorId);
        } else {
            vendor.setVendorId(vendorId.trim());
            if (vendorDAO.existsById(vendor.getVendorId())) {
                throw new VendorConflictException("A vendor with ID '" + vendor.getVendorId() + "' already exists");
            }
        }

        vendor.setActive("Y");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                vendorDAO.insert(conn, vendor);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        return getVendorById(vendor.getVendorId());
    }

    @Override
    public Vendor updateVendor(String vendorId, Vendor vendor) throws SQLException {
        if (vendorId == null || vendorId.trim().isEmpty()) {
            throw new VendorValidationException("Vendor ID is required for update");
        }
        if (vendor == null) {
            throw new VendorValidationException("Vendor update data cannot be empty");
        }

        Vendor existing = vendorDAO.findById(vendorId.trim())
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found with ID: " + vendorId));

        validateVendor(vendor);
        vendor.setVendorId(existing.getVendorId());

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                vendorDAO.update(conn, vendor);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        return getVendorById(vendorId.trim());
    }

    @Override
    public void deactivateVendor(String vendorId) throws SQLException {
        if (vendorId == null || vendorId.trim().isEmpty()) {
            throw new VendorValidationException("Vendor ID is required for deactivation");
        }

        Vendor existing = vendorDAO.findById(vendorId.trim())
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found with ID: " + vendorId));

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                vendorDAO.deactivate(conn, existing.getVendorId());
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void validateVendor(Vendor vendor) {
        if (vendor.getVendorName() == null || vendor.getVendorName().trim().isEmpty()) {
            throw new VendorValidationException("Vendor name is required");
        }
        if (vendor.getVendorName().trim().length() > 150) {
            throw new VendorValidationException("Vendor name must not exceed 150 characters");
        }

        if (vendor.getEmail() != null && !vendor.getEmail().trim().isEmpty()) {
            String email = vendor.getEmail().trim();
            if (email.length() > 150) {
                throw new VendorValidationException("Email must not exceed 150 characters");
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new VendorValidationException("Invalid email format: " + email);
            }
        }

        if (vendor.getContact() != null && !vendor.getContact().trim().isEmpty()) {
            String contact = vendor.getContact().trim();
            if (contact.length() > 20) {
                throw new VendorValidationException("Contact must not exceed 20 characters");
            }
            if (!CONTACT_PATTERN.matcher(contact).matches()) {
                throw new VendorValidationException("Contact must contain a valid phone/mobile format (digits, +, -)");
            }
        }

        if (vendor.getAddress() != null && vendor.getAddress().trim().length() > 300) {
            throw new VendorValidationException("Address must not exceed 300 characters");
        }
    }
}
