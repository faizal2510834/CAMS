package com.cams.model;

import java.sql.Timestamp;

/**
 * Domain entity representing a Campus Physical Location in the LOCATIONS table.
 */
public class Location {

    private String locationId;
    private String locationName;
    private String active; // 'Y' or 'N'
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Location() {
        this.active = "Y";
    }

    public Location(String locationId, String locationName, String active) {
        this.locationId = locationId;
        this.locationName = locationName;
        this.active = active != null ? active : "Y";
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
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
