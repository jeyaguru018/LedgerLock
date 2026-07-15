package com.ledgerlock.service;

import com.ledgerlock.dto.TransferRequest;
import com.ledgerlock.dto.TransferResponse;
import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.User;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrencyStressTest {

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

    private Long userId1;
    private String fromAccountNumber;
    private String toAccountNumber;

    @BeforeEach
    public void setup() {
        // Explicitly clean up all tables (since class is not @Transactional)
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Register users
        User user1 = new User("stress1@ledgerlock.com", "hash1");
        User user2 = new User("stress2@ledgerlock.com", "hash2");
        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        userId1 = user1.getId();

        // Create accounts
        Account accountA = new Account(user1, "ACC-STRESS-A");
        accountA.setBalance(new BigDecimal("1000.0000"));
        accountRepository.save(accountA);
        fromAccountNumber = accountA.getAccountNumber();

        Account accountB = new Account(user2, "ACC-STRESS-B");
        accountB.setBalance(new BigDecimal("500.0000"));
        accountRepository.save(accountB);
        toAccountNumber = accountB.getAccountNumber();
    }

    @Test
    public void testConcurrentIdenticalRequestsOnlyApplyOne() throws InterruptedException, ExecutionException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(30);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        String sharedKey = UUID.randomUUID().toString();
        BigDecimal transferAmount = new BigDecimal("10.0000");

        TransferRequest request = new TransferRequest();
        request.setIdempotencyKey(sharedKey);
        request.setFromAccountNumber(fromAccountNumber);
        request.setToAccountNumber(toAccountNumber);
        request.setAmount(transferAmount);
        request.setDescription("Identical Stress Tx");

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);

        List<Future<TransferResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await(); // Hold all threads here
                try {
                    TransferResponse response = transactionService.executeTransfer(userId1, request);
                    successCounter.incrementAndGet();
                    return response;
                } catch (Exception e) {
                    failureCounter.incrementAndGet();
                    throw e;
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        // Release the latch to fire all requests simultaneously
        startLatch.countDown();
        finishLatch.await(); // Wait for all threads to complete
        executor.shutdown();

        // Reload accounts
        Account updatedA = accountRepository.findByAccountNumber(fromAccountNumber).orElseThrow();
        Account updatedB = accountRepository.findByAccountNumber(toAccountNumber).orElseThrow();

        // Verify that exactly ONE transaction deducted the balance
        BigDecimal expectedA = new BigDecimal("1000.0000").subtract(transferAmount);
        BigDecimal expectedB = new BigDecimal("500.0000").add(transferAmount);

        assertEquals(0, expectedA.compareTo(updatedA.getBalance()), "Source balance should only be deducted once.");
        assertEquals(0, expectedB.compareTo(updatedB.getBalance()), "Dest balance should only be credited once.");
        
        System.out.println("=== PART A CONCURRENCY TEST OUTPUT ===");
        System.out.println("Threads Fired: " + threadCount);
        System.out.println("Shared Idempotency Key: " + sharedKey);
        System.out.println("Successful Operations reported by service: " + successCounter.get());
        System.out.println("Rejected/Clashed Operations: " + failureCounter.get());
        System.out.println("Final Source Account Balance: " + updatedA.getBalance());
        System.out.println("Final Dest Account Balance: " + updatedB.getBalance());
        System.out.println("======================================");
    }

    @Test
    public void testConcurrentDifferentRequestsLostUpdatePrevented() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        BigDecimal transferAmount = new BigDecimal("5.0000");
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger lockConflictCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransferRequest request = new TransferRequest();
                    request.setIdempotencyKey(UUID.randomUUID().toString()); // Different keys
                    request.setFromAccountNumber(fromAccountNumber);
                    request.setToAccountNumber(toAccountNumber);
                    request.setAmount(transferAmount);
                    request.setDescription("Stress Unique Tx " + index);

                    transactionService.executeTransfer(userId1, request);
                    successCounter.incrementAndGet();
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                    lockConflictCounter.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Reload accounts
        Account updatedA = accountRepository.findByAccountNumber(fromAccountNumber).orElseThrow();
        Account updatedB = accountRepository.findByAccountNumber(toAccountNumber).orElseThrow();

        // Mathematical validation of ledger updates
        BigDecimal totalSubtracted = transferAmount.multiply(new BigDecimal(successCounter.get()));
        BigDecimal expectedA = new BigDecimal("1000.0000").subtract(totalSubtracted);
        BigDecimal expectedB = new BigDecimal("500.0000").add(totalSubtracted);

        assertEquals(0, expectedA.compareTo(updatedA.getBalance()), "Final balance A must match subtraction count.");
        assertEquals(0, expectedB.compareTo(updatedB.getBalance()), "Final balance B must match credit count.");

        System.out.println("=== PART B CONCURRENCY TEST OUTPUT ===");
        System.out.println("Threads Fired: " + threadCount);
        System.out.println("Successful commits: " + successCounter.get());
        System.out.println("Optimistic Lock conflicts prevented: " + lockConflictCounter.get());
        System.out.println("Expected Source Balance: " + expectedA);
        System.out.println("Actual Source Balance: " + updatedA.getBalance());
        System.out.println("======================================");
    }

    @Test
    public void testConcurrentOverdrawPrevention() throws InterruptedException {
        // Setup low balance on ACC-STRESS-A
        Account accountA = accountRepository.findByAccountNumber(fromAccountNumber).orElseThrow();
        accountA.setBalance(new BigDecimal("10.0000"));
        accountRepository.saveAndFlush(accountA);

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        BigDecimal transferAmount = new BigDecimal("2.0000"); // 15 * $2.00 = $30.00 (exceeds $10.00)
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger insufficientCounter = new AtomicInteger(0);
        AtomicInteger lockConflictCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransferRequest request = new TransferRequest();
                    request.setIdempotencyKey(UUID.randomUUID().toString());
                    request.setFromAccountNumber(fromAccountNumber);
                    request.setToAccountNumber(toAccountNumber);
                    request.setAmount(transferAmount);
                    request.setDescription("Overdraw test Tx");

                    boolean completed = false;
                    int attempts = 0;
                    while (!completed && attempts < 15) {
                        try {
                            transactionService.executeTransfer(userId1, request);
                            successCounter.incrementAndGet();
                            completed = true;
                        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                            attempts++;
                            // Back off slightly to allow other transactions to commit
                            Thread.sleep(10 + new java.util.Random().nextInt(30));
                        }
                    }
                    if (!completed && attempts >= 15) {
                        lockConflictCounter.incrementAndGet();
                    }
                } catch (com.ledgerlock.exception.InsufficientFundsException e) {
                    insufficientCounter.incrementAndGet();
                } catch (Exception e) {
                    // other errors
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Reload accounts
        Account updatedA = accountRepository.findByAccountNumber(fromAccountNumber).orElseThrow();
        
        // The balance must stop at exactly $0.00 and never go negative (due to chk_non_negative_balance and service checks)
        BigDecimal expectedA = new BigDecimal("10.0000").subtract(transferAmount.multiply(new BigDecimal(successCounter.get())));
        
        assertEquals(0, expectedA.compareTo(updatedA.getBalance()), "Balance must remain consistent with successful mutations.");
        assertEquals(0, new BigDecimal("0.0000").compareTo(updatedA.getBalance()), "Final balance must have been completely depleted to $0.00");
        assertEquals(5, successCounter.get(), "Exactly 5 operations must have succeeded.");

        System.out.println("=== PART C CONCURRENCY TEST OUTPUT ===");
        System.out.println("Threads Fired: " + threadCount);
        System.out.println("Total Balance available: $10.00");
        System.out.println("Successful commits (exactly 5): " + successCounter.get());
        System.out.println("Insufficient Funds blocks: " + insufficientCounter.get());
        System.out.println("Optimistic Lock conflicts prevented & retried: " + lockConflictCounter.get());
        System.out.println("Final Source Account Balance: " + updatedA.getBalance());
        System.out.println("======================================");
    }
}
