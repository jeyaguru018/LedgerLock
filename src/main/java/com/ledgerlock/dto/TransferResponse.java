package com.ledgerlock.dto;

import com.ledgerlock.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class TransferResponse {
    private String transactionId;
    private TransactionStatus status;
    private BigDecimal amount;
    private ZonedDateTime timestamp;
    private String message;

    public TransferResponse() {}

    public TransferResponse(String transactionId, TransactionStatus status, BigDecimal amount, ZonedDateTime timestamp, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.amount = amount;
        this.timestamp = timestamp;
        this.message = message;
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public ZonedDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(ZonedDateTime timestamp) { this.timestamp = timestamp; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
