package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.security.WithMockFamily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ChoreIntegrationTest {
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MALFORMED_CHORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM chore");
        jdbcTemplate.update("DELETE FROM family_member");
        jdbcTemplate.update("DELETE FROM family");

        jdbcTemplate.update(
                "INSERT INTO family (id, name, username, password_hash) VALUES (?, ?, ?, ?)",
                FAMILY_ID,
                "Test Family",
                "testfamily",
                "$2a$10$dummyhashfortesting"
        );

        jdbcTemplate.update(
                "INSERT INTO family_member (id, family_id, name, color, email) VALUES (?, ?, ?, ?, ?)",
                MEMBER_ID,
                FAMILY_ID,
                "Test Member",
                "CORAL",
                "member@test.com"
        );
    }

    @Test
    @WithMockFamily
    void createToggleDeleteChore_roundTripsThroughTheApi() throws Exception {
        String location = mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "🪥 Brush teeth",
                                  "assignedToMemberId": "00000000-0000-0000-0000-000000000002",
                                  "dueDate": "2026-05-05"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("🪥 Brush teeth"))
                .andExpect(jsonPath("$.data.assignedToMemberId").value(MEMBER_ID.toString()))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/chores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(id)).exists());

        mockMvc.perform(patch("/api/chores/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").exists());

        mockMvc.perform(delete("/api/chores/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(id)).doesNotExist());
    }

    @Test
    @WithMockFamily
    void getChores_excludesRowsAssignedToOtherFamilyMembers() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO family (id, name, username, password_hash) VALUES (?, ?, ?, ?)",
                OTHER_FAMILY_ID,
                "Other Family",
                "otherfamily",
                "$2a$10$dummyhashfortesting"
        );

        jdbcTemplate.update(
                "INSERT INTO family_member (id, family_id, name, color, email) VALUES (?, ?, ?, ?, ?)",
                OTHER_MEMBER_ID,
                OTHER_FAMILY_ID,
                "Other Member",
                "TEAL",
                "other@test.com"
        );

        jdbcTemplate.update(
                """
                INSERT INTO chore (id, family_id, assigned_to_member_id, title, due_date, completed)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                MALFORMED_CHORE_ID,
                FAMILY_ID,
                OTHER_MEMBER_ID,
                "Should stay hidden",
                java.sql.Date.valueOf("2026-05-05"),
                false
        );

        mockMvc.perform(get("/api/chores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(MALFORMED_CHORE_ID)).doesNotExist());
    }
}
