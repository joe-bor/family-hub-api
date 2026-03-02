package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class CalendarEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private String memberId;

    @BeforeEach
    void setUp() throws Exception {
        // Register a family and extract token + memberId
        String uniqueUsername = "cal_test_" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "%s",
                                    "password": "password123",
                                    "familyName": "Calendar Family",
                                    "members": [{"name": "Alice", "color": "coral"}]
                                }
                                """.formatted(uniqueUsername)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
        memberId = com.jayway.jsonpath.JsonPath.read(body, "$.data.family.members[0].id");
    }

    @Test
    void fullEventLifecycle() throws Exception {
        // Create event
        MvcResult createResult = mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Team Meeting",
                                    "startTime": "9:00 AM",
                                    "endTime": "10:00 AM",
                                    "date": "2026-03-15",
                                    "memberId": "%s"
                                }
                                """.formatted(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Team Meeting"))
                .andReturn();

        String eventId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.data.id");

        // Query events by date range
        mockMvc.perform(get("/api/calendar/events")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Team Meeting"));

        // Get event by ID
        mockMvc.perform(get("/api/calendar/events/{id}", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Team Meeting"));

        // Delete event
        mockMvc.perform(delete("/api/calendar/events/{id}", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/calendar/events/{id}", eventId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossFamilyAccess_denied() throws Exception {
        // Register a second family
        String otherUsername = "other_" + System.nanoTime();
        MvcResult otherResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "%s",
                                    "password": "password123",
                                    "familyName": "Other Family",
                                    "members": [{"name": "Bob", "color": "teal"}]
                                }
                                """.formatted(otherUsername)))
                .andExpect(status().isOk())
                .andReturn();

        String otherToken = com.jayway.jsonpath.JsonPath.read(
                otherResult.getResponse().getContentAsString(), "$.data.token");

        // Create event with first family
        MvcResult eventResult = mockMvc.perform(post("/api/calendar/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Private Event",
                                    "startTime": "2:00 PM",
                                    "endTime": "3:00 PM",
                                    "date": "2026-03-15",
                                    "memberId": "%s"
                                }
                                """.formatted(memberId)))
                .andExpect(status().isCreated())
                .andReturn();

        String eventId = com.jayway.jsonpath.JsonPath.read(
                eventResult.getResponse().getContentAsString(), "$.data.id");

        // Try to access with second family's token
        mockMvc.perform(get("/api/calendar/events/{id}", eventId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
