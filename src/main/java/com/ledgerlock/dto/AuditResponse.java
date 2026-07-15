package com.ledgerlock.dto;

import java.math.BigDecimal;

public class AuditResponse {
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal difference;
    private boolean systemBalanced;

    public AuditResponse(BigDecimal totalDebits, BigDecimal totalCredits, BigDecimal difference, boolean systemBalanced) {
        this.totalDebits = totalDebits;
        this.totalCredits = totalCredits;
        this.difference = difference;
        this.systemBalanced = systemBalanced;
    }

    public BigDecimal getTotalDebits() { return totalDebits; }
    public void setTotalDebits(BigDecimal totalDebits) { this.totalDebits = totalDebits; }

    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }

    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }

    public boolean isSystemBalanced() { return systemBalanced; }
    public void setSystemBalanced(boolean systemBalanced) { this.systemBalanced = systemBalanced; }
}
