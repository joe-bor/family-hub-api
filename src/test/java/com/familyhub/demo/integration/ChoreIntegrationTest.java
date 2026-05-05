package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.security.WithMockFamily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final String CHORE_ASSIGNEE_INVARIANT_CONSTRAINT = "fk_chore_assignee_in_family";

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
    void createChore_assigneeFromDifferentFamily_returns403ThroughRealStack() throws Exception {
        insertOtherFamilyAndMember();

        mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Should be rejected",
                                  "assignedToMemberId": "00000000-0000-0000-0000-000000000005",
                                  "dueDate": "2026-05-05"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        assertThat(choreCount()).isZero();
    }

    @Test
    void choreTable_rejectsRowsAssignedToDifferentFamilyMembers() {
        insertOtherFamilyAndMember();

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO chore (id, family_id, assigned_to_member_id, title, due_date, completed)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                MALFORMED_CHORE_ID,
                FAMILY_ID,
                OTHER_MEMBER_ID,
                "Should be rejected",
                java.sql.Date.valueOf("2026-05-05"),
                false
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @WithMockFamily
    void getChores_excludesRowsAssignedToOtherFamilyMembers() throws Exception {
        insertOtherFamilyAndMember();
        insertMalformedChoreBypassingConstraint();

        try {
            mockMvc.perform(get("/api/chores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(MALFORMED_CHORE_ID)).doesNotExist());
        } finally {
            cleanupMalformedChoreAndRestoreConstraint();
        }
    }

    @Test
    @WithMockFamily
    void updateChore_malformedRow_returns404() throws Exception {
        insertOtherFamilyAndMember();
        insertMalformedChoreBypassingConstraint();

        try {
            mockMvc.perform(patch("/api/chores/{id}", MALFORMED_CHORE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "completed": true
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Chore not found: " + MALFORMED_CHORE_ID));
        } finally {
            cleanupMalformedChoreAndRestoreConstraint();
        }
    }

    @Test
    @WithMockFamily
    void deleteChore_malformedRow_returns404() throws Exception {
        insertOtherFamilyAndMember();
        insertMalformedChoreBypassingConstraint();

        try {
            mockMvc.perform(delete("/api/chores/{id}", MALFORMED_CHORE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Chore not found: " + MALFORMED_CHORE_ID));

            assertThat(choreCount()).isEqualTo(1);
        } finally {
            cleanupMalformedChoreAndRestoreConstraint();
        }
    }

    private void insertOtherFamilyAndMember() {
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
    }

    private void insertMalformedChoreBypassingConstraint() {
        jdbcTemplate.execute("ALTER TABLE chore DROP CONSTRAINT " + CHORE_ASSIGNEE_INVARIANT_CONSTRAINT);
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
    }

    private void cleanupMalformedChoreAndRestoreConstraint() {
        jdbcTemplate.update("DELETE FROM chore WHERE id = ?", MALFORMED_CHORE_ID);
        jdbcTemplate.execute(
                """
                ALTER TABLE chore
                ADD CONSTRAINT fk_chore_assignee_in_family
                FOREIGN KEY (family_id, assigned_to_member_id)
                REFERENCES family_member(family_id, id)
                ON DELETE CASCADE
                """
        );
    }

    private int choreCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chore", Integer.class);
        return count == null ? 0 : count;
    }
}
