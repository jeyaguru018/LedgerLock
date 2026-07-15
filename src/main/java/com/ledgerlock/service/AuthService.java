package com.ledgerlock.service;

import com.ledgerlock.dto.*;
import com.ledgerlock.entity.RefreshToken;
import com.ledgerlock.entity.User;
import com.ledgerlock.exception.TokenRefreshException;
import com.ledgerlock.repository.RefreshTokenRepository;
import com.ledgerlock.repository.UserRepository;
import com.ledgerlock.security.JwtUtils;
import com.ledgerlock.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-expiration-ms}")
    private Long refreshTokenDurationMs;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder encoder, JwtUtils jwtUtils) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public MessageResponse registerUser(SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            logger.warn("Signup failed: Email {} is already in use", signUpRequest.getEmail());
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        User user = new User(signUpRequest.getEmail(), encoder.encode(signUpRequest.getPassword()));
        userRepository.save(user);

        logger.info("User registered successfully: {}", signUpRequest.getEmail());
        return new MessageResponse("User registered successfully!");
    }

    @Transactional
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            
            // Revoke existing tokens for user
            User user = userRepository.findById(userDetails.getId()).orElseThrow();
            refreshTokenRepository.deleteByUser(user);
            
            String rawRefreshToken = createRefreshToken(userDetails.getId());

            logger.info("User logged in successfully: {}", loginRequest.getEmail());
            return new JwtResponse(jwt, rawRefreshToken, userDetails.getId(), userDetails.getUsername());
        } catch (org.springframework.security.core.AuthenticationException e) {
            logger.warn("Login failed for email: {} - {}", loginRequest.getEmail(), e.getMessage());
            throw e;
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public String createRefreshToken(Long userId) {
        RefreshToken refreshToken = new RefreshToken();
        User user = userRepository.findById(userId).orElseThrow();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(ZonedDateTime.now().plusSeconds(refreshTokenDurationMs / 1000));
        
        String rawToken = UUID.randomUUID().toString();
        refreshToken.setTokenHash(hashToken(rawToken));
        
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(ZonedDateTime.now()) < 0) {
            refreshTokenRepository.delete(token);
            logger.warn("Refresh token expired for user ID: {}", token.getUser().getId());
            throw new TokenRefreshException(token.getTokenHash(), "Refresh token was expired. Please make a new signin request");
        }
        if (token.isRevoked()) {
            throw new TokenRefreshException(token.getTokenHash(), "Refresh token was revoked.");
        }
        return token;
    }

    @Transactional
    public JwtResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        String hashedRequestToken = hashToken(requestRefreshToken);

        return refreshTokenRepository.findByTokenHash(hashedRequestToken)
                .map(this::verifyExpiration)
                .map(token -> {
                    User user = token.getUser();
                    // Rotate refresh token: delete old, flush, and issue new
                    refreshTokenRepository.delete(token);
                    refreshTokenRepository.flush();
                    String newRawRefreshToken = createRefreshToken(user.getId());
                    
                    String jwt = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(UserDetailsImpl.build(user), null, java.util.Collections.emptyList()));
                    logger.info("Token refreshed successfully for user: {}", user.getEmail());
                    return new JwtResponse(jwt, newRawRefreshToken, user.getId(), user.getEmail());
                })
                .orElseThrow(() -> {
                    logger.warn("Token refresh failed: token not found in database or invalid hash");
                    return new TokenRefreshException(requestRefreshToken, "Refresh token is not in database!");
                });
    }
    
    @Transactional
    public void logoutUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        refreshTokenRepository.deleteByUser(user);
    }
}
