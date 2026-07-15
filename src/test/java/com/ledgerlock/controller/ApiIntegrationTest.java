package com.ledgerlock.controller;

import com.ledgerlock.entity.User;
import com.ledgerlock.repository.UserRepository;
import com.ledgerlock.security.JwtUtils;
import com.ledgerlock.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    private String jwtToken;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
        User user = new User("ratelimituser@ledgerlock.com", "hash");
        user = userRepository.save(user);

        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        jwtToken = jwtUtils.generateJwtToken(authentication);
    }

    @Test
    public void testRateLimitingEnforcement() throws Exception {
        // We configured the bucket to allow 10 requests per minute.
        // Let's fire 10 requests, they should all be 200 OK.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/accounts/me")
                    .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk());
        }

        // The 11th request should be blocked with 429 Too Many Requests
        mockMvc.perform(get("/api/accounts/me")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isTooManyRequests());
    }
}
