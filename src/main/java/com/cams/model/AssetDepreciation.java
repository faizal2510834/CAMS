package com.cams.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the calculated straight-line depreciation metrics and yearly schedule for an asset.
 */
public class AssetDepreciation implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assetId;
    private String assetName;
    private String category;
    private String department;
    private Date purchaseDate;
    private BigDecimal purchaseCost;
    private int usefulLifeYears;
    private BigDecimal annualDepreciation;
    private double yearsElapsed;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal currentValue;
    private BigDecimal residualValue;
    private List<DepreciationScheduleItem> schedule = new ArrayList<>();

    public AssetDepreciation() {
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Date getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(Date purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public int getUsefulLifeYears() {
        return usefulLifeYears;
    }

    public void setUsefulLifeYears(int usefulLifeYears) {
        this.usefulLifeYears = usefulLifeYears;
    }

    public BigDecimal getAnnualDepreciation() {
        return annualDepreciation;
    }

    public void setAnnualDepreciation(BigDecimal annualDepreciation) {
        this.annualDepreciation = annualDepreciation;
    }

    public double getYearsElapsed() {
        return yearsElapsed;
    }

    public void setYearsElapsed(double yearsElapsed) {
        this.yearsElapsed = yearsElapsed;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) {
        this.accumulatedDepreciation = accumulatedDepreciation;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getResidualValue() {
        return residualValue;
    }

    public void setResidualValue(BigDecimal residualValue) {
        this.residualValue = residualValue;
    }

    public List<DepreciationScheduleItem> getSchedule() {
        return schedule;
    }

    public void setSchedule(List<DepreciationScheduleItem> schedule) {
        this.schedule = schedule;
    }
}
