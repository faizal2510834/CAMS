package com.cams.model;

import java.io.Serializable;
import java.sql.Date;

/**
 * Category-specific detail model for Laboratory Equipment assets.
 */
public class LabDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private String equipmentType;
    private String condition;
    private Date lastCalibrationDate;

    public LabDetails() {
    }

    public LabDetails(String equipmentType, String condition, Date lastCalibrationDate) {
        this.equipmentType = equipmentType;
        this.condition = condition;
        this.lastCalibrationDate = lastCalibrationDate;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String equipmentType) {
        this.equipmentType = equipmentType;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public Date getLastCalibrationDate() {
        return lastCalibrationDate;
    }

    public void setLastCalibrationDate(Date lastCalibrationDate) {
        this.lastCalibrationDate = lastCalibrationDate;
    }
}
