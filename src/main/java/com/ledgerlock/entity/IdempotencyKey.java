package com.ledgerlock.entity;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.time.ZonedDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey implements Persistable<String> {

    @Id
    @Column(length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Transient
    private boolean isNew = true;

    public IdempotencyKey() {}

    public IdempotencyKey(String idempotencyKey, TransactionStatus status) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
    }

    @Override
    public String getId() {
        return idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    // Getters and Setters
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
}
