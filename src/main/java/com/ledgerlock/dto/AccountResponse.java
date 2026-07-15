package com.ledgerlock.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class AccountResponse {
    private String accountNumber;
    private BigDecimal balance;
    private ZonedDateTime createdAt;

    public AccountResponse(String accountNumber, BigDecimal balance, ZonedDateTime createdAt) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
