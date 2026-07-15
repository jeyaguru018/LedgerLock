package com.ledgerlock.service;

import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.User;
import com.ledgerlock.repository.AccountRepository;
import com.ledgerlock.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    public List<Account> getAccountsForUser(Long userId) {
        return accountRepository.findByUserId(userId);
    }

    @Transactional
    public Account createAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        // Generate random account number: ACC-XXXX-YY
        Random random = new Random();
        String accountNumber = "ACC-" + (1000 + random.nextInt(9000)) + "-" + 
                (char)('A' + random.nextInt(26)) + (char)('A' + random.nextInt(26));
                
        Account account = new Account(user, accountNumber);
        account.setBalance(new BigDecimal("10000.0000")); // Seed with $10,000.00 demo cash
        return accountRepository.save(account);
    }
}
