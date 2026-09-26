package com.cams.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Campus-wide depreciation and asset valuation aggregate summary.
 */
public class DepreciationSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal totalPurchaseCost = BigDecimal.ZERO;
    private BigDecimal totalCurrentValue = BigDecimal.ZERO;
    private BigDecimal totalAccumulatedDepreciation = BigDecimal.ZERO;
    private int assetCount = 0;
    private List<DepreciationCategorySummary> categoryBreakdown = new ArrayList<>();

    public DepreciationSummary() {
    }

    public BigDecimal getTotalPurchaseCost() {
        return totalPurchaseCost;
    }

    public void setTotalPurchaseCost(BigDecimal totalPurchaseCost) {
        this.totalPurchaseCost = totalPurchaseCost;
    }

    public BigDecimal getTotalCurrentValue() {
        return totalCurrentValue;
    }

    public void setTotalCurrentValue(BigDecimal totalCurrentValue) {
        this.totalCurrentValue = totalCurrentValue;
    }

    public BigDecimal getTotalAccumulatedDepreciation() {
        return totalAccumulatedDepreciation;
    }

    public void setTotalAccumulatedDepreciation(BigDecimal totalAccumulatedDepreciation) {
        this.totalAccumulatedDepreciation = totalAccumulatedDepreciation;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public List<DepreciationCategorySummary> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    public void setCategoryBreakdown(List<DepreciationCategorySummary> categoryBreakdown) {
        this.categoryBreakdown = categoryBreakdown;
    }
}
