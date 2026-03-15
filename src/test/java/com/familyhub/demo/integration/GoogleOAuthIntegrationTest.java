package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.MediaType;

import javax.sql.DataSource;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class GoogleOAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private String token;
    private String memberId;

    @BeforeEach
    void setUp() throws Exception {
        String username = "u" + (System.nanoTime() % 10_000_000_000L);
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "%s",
                                    "password": "password123",
                                    "familyName": "OAuth Family",
                                    "members": [
                                        { "name": "Member", "color": "teal", "email": "m@test.com" }
                                    ]
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        token = JsonPath.read(body, "$.data.token");
        memberId = JsonPath.read(body, "$.data.family.members[0].id");
    }

    @Test
    void getAuthUrl_returnsValidUrl() throws Exception {
        mockMvc.perform(get("/api/google/auth")
                        .header("Authorization", "Bearer " + token)
                        .param("memberId", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").exists());
    }

    @Test
    void getAuthUrl_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/google/auth")
                        .param("memberId", memberId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_noToken_returnsFalse() throws Exception {
        mockMvc.perform(get("/api/google/status/{memberId}", memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    void fullConnectionLifecycle() throws Exception {
        // 1. Status should be disconnected
        mockMvc.perform(get("/api/google/status/{memberId}", memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));

        // 2. Manually insert a token row (simulates successful OAuth callback)
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT INTO google_oauth_token (id, member_id, access_token, refresh_token, token_expiry, scope) " +
                             "VALUES (gen_random_uuid(), ?::uuid, 'encrypted-access', 'encrypted-refresh', ?, 'calendar.events.readonly')")) {
            stmt.setString(1, memberId);
            stmt.setObject(2, java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
            stmt.executeUpdate();
        }

        // 3. Status should be connected
        mockMvc.perform(get("/api/google/status/{memberId}", memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));

        // 4. Disconnect
        mockMvc.perform(delete("/api/google/disconnect/{memberId}", memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 5. Status should be disconnected again
        mockMvc.perform(get("/api/google/status/{memberId}", memberId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));
    }

    @Test
    void crossFamilyAccess_denied() throws Exception {
        // Register a second family
        String otherUsername = "o" + (System.nanoTime() % 10_000_000_000L);
        String otherBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "%s",
                                    "password": "password123",
                                    "familyName": "Other Family",
                                    "members": [
                                        { "name": "Other", "color": "coral", "email": "other@test.com" }
                                    ]
                                }
                                """.formatted(otherUsername)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String otherToken = JsonPath.read(otherBody, "$.data.token");

        // Try to access first family's member status with second family's token
        mockMvc.perform(get("/api/google/status/{memberId}", memberId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void callback_accessibleWithoutAuth() throws Exception {
        // Callback should not require JWT — it's a Google redirect
        // Will fail because "invalid-code" is not a real Google code,
        // but should NOT return 401 (should get a 400 or 500 instead)
        int status = mockMvc.perform(get("/api/google/callback")
                        .param("code", "invalid-code")
                        .param("state", memberId))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(401);
    }
}
