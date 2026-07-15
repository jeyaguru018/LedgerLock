package com.ledgerlock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledgerlock.dto.TransferRequest;
import com.ledgerlock.dto.TransferResponse;
import com.ledgerlock.entity.*;
import com.ledgerlock.exception.AccountNotFoundException;
import com.ledgerlock.exception.IdempotencyConflictException;
import com.ledgerlock.exception.InsufficientFundsException;
import com.ledgerlock.repository.AccountRepository;
import com.ledgerlock.repository.IdempotencyKeyRepository;
import com.ledgerlock.repository.LedgerEntryRepository;
import com.ledgerlock.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                              LedgerEntryRepository ledgerEntryRepository, IdempotencyKeyRepository idempotencyKeyRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private TransactionService self;

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            backoff = @Backoff(delay = 100, maxDelay = 500, multiplier = 2.0)
    )
    public TransferResponse executeTransfer(Long currentUserId, TransferRequest request) {
        return self.executeTransferInternal(currentUserId, request);
    }

    @Transactional
    public TransferResponse executeTransferInternal(Long currentUserId, TransferRequest request) {
        
        // 1. Check if key already exists (legitimate retry flow)
        Optional<IdempotencyKey> existingKeyOpt = idempotencyKeyRepository.findById(request.getIdempotencyKey());
        if (existingKeyOpt.isPresent()) {
            IdempotencyKey existingKey = existingKeyOpt.get();
            if (existingKey.getStatus() == TransactionStatus.COMPLETED) {
                try {
                    logger.info("Legitimate retry detected for key: {}. Returning cached response.", request.getIdempotencyKey());
                    return objectMapper.readValue(existingKey.getResponseBody(), TransferResponse.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to deserialize cached response.", e);
                }
            } else {
                logger.warn("Idempotency conflict (PENDING/FAILED) detected for key: {}", request.getIdempotencyKey());
                throw new IdempotencyConflictException("A transaction with this idempotency key is already in progress.");
            }
        }

        // 2. Enforce Idempotency via Database Constraints for concurrent races
        IdempotencyKey idempotencyKey = new IdempotencyKey(request.getIdempotencyKey(), TransactionStatus.PENDING);
        try {
            idempotencyKeyRepository.saveAndFlush(idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            logger.warn("Concurrent idempotency conflict detected for key: {}", request.getIdempotencyKey());
            throw new IdempotencyConflictException("A transaction with this idempotency key is already in progress.");
        }

        try {
            // 3. Fetch Accounts
            Account fromAccount = accountRepository.findByAccountNumberAndUserId(request.getFromAccountNumber(), currentUserId)
                    .orElseThrow(() -> new AccountNotFoundException("Source account not found or access denied."));

            Account toAccount = accountRepository.findByAccountNumber(request.getToAccountNumber())
                    .orElseThrow(() -> new AccountNotFoundException("Destination account not found."));

            if (fromAccount.getId().equals(toAccount.getId())) {
                throw new IllegalArgumentException("Cannot transfer to the same account.");
            }

            BigDecimal amount = request.getAmount();

            // 4. Application-Level Funds Check
            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient funds in source account.");
            }

            // 5. Update Balances (Optimistic Locking enforced by @Version)
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            toAccount.setBalance(toAccount.getBalance().add(amount));

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            // 6. Create Transaction Record
            Transaction transaction = new Transaction(
                    request.getIdempotencyKey(),
                    fromAccount,
                    toAccount,
                    amount,
                    TransactionStatus.COMPLETED,
                    request.getDescription()
            );
            transaction = transactionRepository.save(transaction);

            // 7. Create Double-Entry Ledger Lines
            LedgerEntry debitEntry = new LedgerEntry(transaction, fromAccount, TransactionType.DEBIT, amount, fromAccount.getBalance());
            LedgerEntry creditEntry = new LedgerEntry(transaction, toAccount, TransactionType.CREDIT, amount, toAccount.getBalance());
            ledgerEntryRepository.save(debitEntry);
            ledgerEntryRepository.save(creditEntry);

            // 8. Cache response and mark Idempotency Key as COMPLETED
            TransferResponse response = new TransferResponse(
                    transaction.getId().toString(),
                    TransactionStatus.COMPLETED,
                    amount,
                    ZonedDateTime.now(),
                    "Transfer successful"
            );
            
            idempotencyKey.setStatus(TransactionStatus.COMPLETED);
            idempotencyKey.setResponseCode(200);
            try {
                idempotencyKey.setResponseBody(objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize response for caching.", e);
            }
            idempotencyKeyRepository.save(idempotencyKey);

            logger.info("Transfer completed successfully. TX ID: {}, Key: {}, From: {}, To: {}, Amount: {}", 
                    transaction.getId(),
                    request.getIdempotencyKey(),
                    maskAccountNumber(fromAccount.getAccountNumber()),
                    maskAccountNumber(toAccount.getAccountNumber()),
                    amount);
            return response;

        } catch (Exception e) {
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<com.ledgerlock.dto.LedgerEntryResponse> getAccountHistory(Long currentUserId, String accountNumber) {
        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, currentUserId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found or access denied."));

        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(account.getId()).stream()
                .map(entry -> new com.ledgerlock.dto.LedgerEntryResponse(
                        entry.getTransaction().getId().toString(),
                        entry.getType(),
                        entry.getAmount(),
                        entry.getBalanceAfter(),
                        entry.getCreatedAt(),
                        entry.getTransaction().getDescription()
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public com.ledgerlock.dto.AuditResponse getAuditReport() {
        java.util.List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        
        java.math.BigDecimal totalDebits = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalCredits = java.math.BigDecimal.ZERO;
        
        for (LedgerEntry entry : entries) {
            if (entry.getType() == TransactionType.DEBIT) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else if (entry.getType() == TransactionType.CREDIT) {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }
        
        java.math.BigDecimal difference = totalDebits.subtract(totalCredits).abs();
        boolean systemBalanced = difference.compareTo(new java.math.BigDecimal("0.0001")) < 0;
        
        return new com.ledgerlock.dto.AuditResponse(totalDebits, totalCredits, difference, systemBalanced);
    }

    private String maskAccountNumber(String accountNum) {
        if (accountNum == null) return "null";
        if (accountNum.length() <= 4) return accountNum;
        return "*".repeat(accountNum.length() - 4) + accountNum.substring(accountNum.length() - 4);
    }
}
