package com.ledgerlock.controller;

import com.ledgerlock.dto.AccountResponse;
import com.ledgerlock.security.UserDetailsImpl;
import com.ledgerlock.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        Long currentUserId = userDetails.getId();

        List<AccountResponse> accounts = accountService.getAccountsForUser(currentUserId).stream()
                .map(acc -> new AccountResponse(acc.getAccountNumber(), acc.getBalance(), acc.getCreatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        Long currentUserId = userDetails.getId();

        com.ledgerlock.entity.Account acc = accountService.createAccount(currentUserId);
        AccountResponse response = new AccountResponse(acc.getAccountNumber(), acc.getBalance(), acc.getCreatedAt());
        return ResponseEntity.ok(response);
    }
}
