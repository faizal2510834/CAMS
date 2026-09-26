package com.cams.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Represents a single period/year within an asset's straight-line depreciation schedule.
 */
public class DepreciationScheduleItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private int yearNumber;
    private String date;
    private BigDecimal depreciationExpense;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal endingBookValue;

    public DepreciationScheduleItem() {
    }

    public DepreciationScheduleItem(int yearNumber, String date, BigDecimal depreciationExpense,
                                    BigDecimal accumulatedDepreciation, BigDecimal endingBookValue) {
        this.yearNumber = yearNumber;
        this.date = date;
        this.depreciationExpense = depreciationExpense;
        this.accumulatedDepreciation = accumulatedDepreciation;
        this.endingBookValue = endingBookValue;
    }

    public int getYearNumber() {
        return yearNumber;
    }

    public void setYearNumber(int yearNumber) {
        this.yearNumber = yearNumber;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getDepreciationExpense() {
        return depreciationExpense;
    }

    public void setDepreciationExpense(BigDecimal depreciationExpense) {
        this.depreciationExpense = depreciationExpense;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) {
        this.accumulatedDepreciation = accumulatedDepreciation;
    }

    public BigDecimal getEndingBookValue() {
        return endingBookValue;
    }

    public void setEndingBookValue(BigDecimal endingBookValue) {
        this.endingBookValue = endingBookValue;
    }
}
