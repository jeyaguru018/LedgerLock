package com.ledgerlock.service;

import com.ledgerlock.dto.*;
import com.ledgerlock.entity.RefreshToken;
import com.ledgerlock.exception.TokenRefreshException;
import com.ledgerlock.repository.RefreshTokenRepository;
import com.ledgerlock.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    public void setup() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testSuccessfulSignup() {
        SignupRequest request = new SignupRequest();
        request.setEmail("test@ledgerlock.com");
        request.setPassword("securePassword123");

        MessageResponse response = authService.registerUser(request);
        assertEquals("User registered successfully!", response.getMessage());
        assertTrue(userRepository.existsByEmail("test@ledgerlock.com"));
    }

    @Test
    public void testDuplicateEmailRejection() {
        SignupRequest request = new SignupRequest();
        request.setEmail("duplicate@ledgerlock.com");
        request.setPassword("securePassword123");
        authService.registerUser(request);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            authService.registerUser(request);
        });
        assertTrue(thrown.getMessage().contains("Email is already in use"));
    }

    @Test
    public void testSuccessfulLoginAndTokenGeneration() {
        SignupRequest signup = new SignupRequest();
        signup.setEmail("login@ledgerlock.com");
        signup.setPassword("securePassword123");
        authService.registerUser(signup);

        LoginRequest login = new LoginRequest();
        login.setEmail("login@ledgerlock.com");
        login.setPassword("securePassword123");

        JwtResponse response = authService.authenticateUser(login);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("login@ledgerlock.com", response.getEmail());
    }

    @Test
    public void testWrongPasswordRejection() {
        SignupRequest signup = new SignupRequest();
        signup.setEmail("wrongpass@ledgerlock.com");
        signup.setPassword("securePassword123");
        authService.registerUser(signup);

        LoginRequest login = new LoginRequest();
        login.setEmail("wrongpass@ledgerlock.com");
        login.setPassword("wrongPassword");

        assertThrows(BadCredentialsException.class, () -> {
            authService.authenticateUser(login);
        });
    }

    @Test
    public void testTokenRefreshFlow() {
        // 1. Signup & Login
        SignupRequest signup = new SignupRequest();
        signup.setEmail("refresh@ledgerlock.com");
        signup.setPassword("securePassword123");
        authService.registerUser(signup);

        LoginRequest login = new LoginRequest();
        login.setEmail("refresh@ledgerlock.com");
        login.setPassword("securePassword123");
        JwtResponse loginResponse = authService.authenticateUser(login);

        // 2. Refresh
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(loginResponse.getRefreshToken());

        JwtResponse refreshResponse = authService.refreshToken(refreshRequest);

        assertNotNull(refreshResponse.getAccessToken());
        assertNotNull(refreshResponse.getRefreshToken());
        // Verify token rotated (changed)
        assertNotEquals(loginResponse.getRefreshToken(), refreshResponse.getRefreshToken());
    }

    @Test
    public void testExpiredTokenRejection() {
        // Setup user
        SignupRequest signup = new SignupRequest();
        signup.setEmail("expired@ledgerlock.com");
        signup.setPassword("securePassword123");
        authService.registerUser(signup);
        Long userId = userRepository.findByEmail("expired@ledgerlock.com").get().getId();

        // Create manually expired token
        String rawToken = authService.createRefreshToken(userId);
        RefreshToken expiredToken = refreshTokenRepository.findAll().get(0);
        expiredToken.setExpiryDate(ZonedDateTime.now().minusDays(1)); // Expired yesterday
        refreshTokenRepository.save(expiredToken);

        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(rawToken);

        assertThrows(TokenRefreshException.class, () -> {
            authService.refreshToken(refreshRequest);
        });
    }

    @Test
    public void testLogoutInvalidation() {
        SignupRequest signup = new SignupRequest();
        signup.setEmail("logout@ledgerlock.com");
        signup.setPassword("securePassword123");
        authService.registerUser(signup);
        Long userId = userRepository.findByEmail("logout@ledgerlock.com").get().getId();

        authService.createRefreshToken(userId); // User logged in
        
        // Log out
        authService.logoutUser(userId);
        
        // Ensure no refresh tokens exist for user
        assertTrue(refreshTokenRepository.findAll().isEmpty());
    }
}
