package com.cams.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Aggregated depreciation values for a specific asset category.
 */
public class DepreciationCategorySummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private String category;
    private int assetCount;
    private BigDecimal originalCost;
    private BigDecimal currentValue;
    private BigDecimal depreciatedAmount;

    public DepreciationCategorySummary() {
    }

    public DepreciationCategorySummary(String category, int assetCount, BigDecimal originalCost,
                                       BigDecimal currentValue, BigDecimal depreciatedAmount) {
        this.category = category;
        this.assetCount = assetCount;
        this.originalCost = originalCost;
        this.currentValue = currentValue;
        this.depreciatedAmount = depreciatedAmount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public BigDecimal getOriginalCost() {
        return originalCost;
    }

    public void setOriginalCost(BigDecimal originalCost) {
        this.originalCost = originalCost;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getDepreciatedAmount() {
        return depreciatedAmount;
    }

    public void setDepreciatedAmount(BigDecimal depreciatedAmount) {
        this.depreciatedAmount = depreciatedAmount;
    }
}
