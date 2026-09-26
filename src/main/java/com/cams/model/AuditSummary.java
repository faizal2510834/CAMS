package com.cams.model;

import java.io.Serializable;

/**
 * Summary statistics for the audit operations dashboard.
 */
public class AuditSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private int totalAudits;
    private int verifiedCount;
    private int missingCount;
    private int mislocatedCount;

    public AuditSummary() {
    }

    public AuditSummary(int totalAudits, int verifiedCount, int missingCount, int mislocatedCount) {
        this.totalAudits = totalAudits;
        this.verifiedCount = verifiedCount;
        this.missingCount = missingCount;
        this.mislocatedCount = mislocatedCount;
    }

    public int getTotalAudits() {
        return totalAudits;
    }

    public void setTotalAudits(int totalAudits) {
        this.totalAudits = totalAudits;
    }

    public int getVerifiedCount() {
        return verifiedCount;
    }

    public void setVerifiedCount(int verifiedCount) {
        this.verifiedCount = verifiedCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(int missingCount) {
        this.missingCount = missingCount;
    }

    public int getMislocatedCount() {
        return mislocatedCount;
    }

    public void setMislocatedCount(int mislocatedCount) {
        this.mislocatedCount = mislocatedCount;
    }
}
