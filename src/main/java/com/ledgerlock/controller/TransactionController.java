package com.ledgerlock.controller;

import com.ledgerlock.dto.LedgerEntryResponse;
import com.ledgerlock.dto.TransferRequest;
import com.ledgerlock.dto.TransferResponse;
import com.ledgerlock.security.UserDetailsImpl;
import com.ledgerlock.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        Long currentUserId = userDetails.getId();

        TransferResponse response = transactionService.executeTransfer(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<List<LedgerEntryResponse>> getHistory(@PathVariable String accountNumber) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        Long currentUserId = userDetails.getId();

        List<LedgerEntryResponse> history = transactionService.getAccountHistory(currentUserId, accountNumber);
        return ResponseEntity.ok(history);
    }

    @Value("${app.admin-email:admin@ledgerlock.com}")
    private String adminEmail;

    @GetMapping("/audit")
    public ResponseEntity<com.ledgerlock.dto.AuditResponse> auditSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();

        if (!adminEmail.equalsIgnoreCase(userDetails.getUsername())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(transactionService.getAuditReport());
    }
}
