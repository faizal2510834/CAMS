package com.cams.model;

import java.sql.Timestamp;

/**
 * Model representing an external equipment/service supplier in the VENDORS table.
 */
public class Vendor {

    private String vendorId;
    private String vendorName;
    private String contact;
    private String email;
    private String address;
    private String active; // 'Y' or 'N'
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Vendor() {
        this.active = "Y";
    }

    public Vendor(String vendorId, String vendorName, String contact, String email, String address, String active) {
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.contact = contact;
        this.email = email;
        this.address = address;
        this.active = active != null ? active : "Y";
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public boolean isActive() {
        return "Y".equalsIgnoreCase(this.active);
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
