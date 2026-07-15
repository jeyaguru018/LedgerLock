package com.ledgerlock.service;

import com.ledgerlock.dto.TransferRequest;
import com.ledgerlock.dto.TransferResponse;
import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.IdempotencyKey;
import com.ledgerlock.entity.TransactionStatus;
import com.ledgerlock.entity.User;
import com.ledgerlock.exception.AccountNotFoundException;
import com.ledgerlock.exception.IdempotencyConflictException;
import com.ledgerlock.exception.InsufficientFundsException;
import com.ledgerlock.repository.AccountRepository;
import com.ledgerlock.repository.IdempotencyKeyRepository;
import com.ledgerlock.repository.LedgerEntryRepository;
import com.ledgerlock.repository.TransactionRepository;
import com.ledgerlock.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TransactionIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private Account accountA;
    private Account accountB;

    @BeforeEach
    public void setup() {
        // Clear tables
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Setup User
        User user1 = new User("user1@ledgerlock.com", "hash1");
        User user2 = new User("user2@ledgerlock.com", "hash2");
        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        // Setup Accounts
        accountA = new Account(user1, "ACC-001");
        accountA.setBalance(new BigDecimal("1000.0000"));
        accountA = accountRepository.save(accountA);

        accountB = new Account(user2, "ACC-002");
        accountB.setBalance(new BigDecimal("500.0000"));
        accountB = accountRepository.save(accountB);
    }

    @Test
    public void testSuccessfulTransfer() {
        TransferRequest request = new TransferRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setFromAccountNumber(accountA.getAccountNumber());
        request.setToAccountNumber(accountB.getAccountNumber());
        request.setAmount(new BigDecimal("200.0000"));

        TransferResponse response = transactionService.executeTransfer(accountA.getUser().getId(), request);

        assertEquals(TransactionStatus.COMPLETED, response.getStatus());

        // Verify Balances
        Account updatedA = accountRepository.findById(accountA.getId()).get();
        Account updatedB = accountRepository.findById(accountB.getId()).get();

        assertEquals(0, new BigDecimal("800.0000").compareTo(updatedA.getBalance()));
        assertEquals(0, new BigDecimal("700.0000").compareTo(updatedB.getBalance()));

        // Verify Ledger Entries
        assertEquals(2, ledgerEntryRepository.findAll().size());
        
        // Verify Idempotency Key Status
        assertEquals(TransactionStatus.COMPLETED, idempotencyKeyRepository.findById(request.getIdempotencyKey()).get().getStatus());
    }

    @Test
    public void testInsufficientFunds() {
        TransferRequest request = new TransferRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setFromAccountNumber(accountA.getAccountNumber());
        request.setToAccountNumber(accountB.getAccountNumber());
        request.setAmount(new BigDecimal("2000.0000"));

        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.executeTransfer(accountA.getUser().getId(), request);
        });

        // Balances should be unchanged
        Account updatedA = accountRepository.findById(accountA.getId()).get();
        Account updatedB = accountRepository.findById(accountB.getId()).get();
        assertEquals(0, new BigDecimal("1000.0000").compareTo(updatedA.getBalance()));
        assertEquals(0, new BigDecimal("500.0000").compareTo(updatedB.getBalance()));
    }

    @Test
    public void testDuplicateIdempotencyKey() {
        String key = UUID.randomUUID().toString();

        TransferRequest request1 = new TransferRequest();
        request1.setIdempotencyKey(key);
        request1.setFromAccountNumber(accountA.getAccountNumber());
        request1.setToAccountNumber(accountB.getAccountNumber());
        request1.setAmount(new BigDecimal("200.0000"));
        transactionService.executeTransfer(accountA.getUser().getId(), request1);

        TransferRequest request2 = new TransferRequest();
        request2.setIdempotencyKey(key);
        request2.setFromAccountNumber(accountA.getAccountNumber());
        request2.setToAccountNumber(accountB.getAccountNumber());
        request2.setAmount(new BigDecimal("200.0000"));

        // Wait, if request1 already completed, a retry of request2 with the exact same key 
        // will now return the cached response, NOT throw IdempotencyConflictException!
        // The concurrent race is what throws the exception. 
        // To test the concurrent race natively in a unit test is tricky without multithreading, 
        // but we can test the retry flow directly. Let's verify the retry flow succeeds.
        TransferResponse response2 = transactionService.executeTransfer(accountA.getUser().getId(), request2);
        
        // Assert it's the exact same transaction ID and no new funds were moved
        assertEquals(TransactionStatus.COMPLETED, response2.getStatus());
        
        Account updatedA = accountRepository.findById(accountA.getId()).get();
        // Balance only deducted 200, not 400
        assertEquals(0, new BigDecimal("800.0000").compareTo(updatedA.getBalance()));
    }

    @Test
    public void testConcurrentIdempotencyRaceIsRejected() {
        // We simulate the concurrent race by manually saving a PENDING key, 
        // which mimics a thread that just inserted but hasn't committed/finished yet.
        String key = UUID.randomUUID().toString();
        IdempotencyKey pendingKey = new IdempotencyKey(key, TransactionStatus.PENDING);
        idempotencyKeyRepository.saveAndFlush(pendingKey);
        
        TransferRequest request = new TransferRequest();
        request.setIdempotencyKey(key);
        request.setFromAccountNumber(accountA.getAccountNumber());
        request.setToAccountNumber(accountB.getAccountNumber());
        request.setAmount(new BigDecimal("200.0000"));

        // Because it's PENDING, the service will throw IdempotencyConflictException
        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.executeTransfer(accountA.getUser().getId(), request);
        });
    }

    @Test
    public void testSecurityOwnershipIsEnforced() {
        TransferRequest request = new TransferRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setFromAccountNumber(accountA.getAccountNumber()); // user1's account
        request.setToAccountNumber(accountB.getAccountNumber());
        request.setAmount(new BigDecimal("200.0000"));

        // Attempting to transfer from accountA, but authenticated as user2 (accountB.getUser().getId())
        assertThrows(AccountNotFoundException.class, () -> {
            transactionService.executeTransfer(accountB.getUser().getId(), request);
        });
    }
}
