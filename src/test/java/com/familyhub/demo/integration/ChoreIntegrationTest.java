package com.familyhub.demo.integration;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.security.WithMockFamily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.FAMILY_ID;
import static com.familyhub.demo.TestDataFactory.MEMBER_ID;
import static com.familyhub.demo.TestDataFactory.OTHER_FAMILY_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, ChoreIntegrationTest.FixedClockConfig.class})
@ActiveProfiles("test")
class ChoreIntegrationTest {
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM chore_period_completion");
        jdbcTemplate.update("DELETE FROM chore_template");
        jdbcTemplate.update("DELETE FROM family_member");
        jdbcTemplate.update("DELETE FROM family");

        jdbcTemplate.update(
                "INSERT INTO family (id, name, username, password_hash, timezone) VALUES (?, ?, ?, ?, ?)",
                FAMILY_ID,
                "Test Family",
                "testfamily",
                "$2a$10$dummyhashfortesting",
                "America/Los_Angeles"
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
    void recurringTemplate_roundTripsBoardCompletionUncompletionAndArchive() throws Exception {
        String location = mockMvc.perform(post("/api/chores/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Brush teeth",
                                  "assignedToMemberId": "00000000-0000-0000-0000-000000000002",
                                  "cadence": "DAILY",
                                  "activeFrom": "2026-05-19"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Brush teeth"))
                .andExpect(jsonPath("$.data.cadence").value("DAILY"))
                .andExpect(jsonPath("$.data.archived").value(false))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String templateId = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/chores/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.data.today.periodStartDate").value("2026-05-19"))
                .andExpect(jsonPath("$.data.today.summary.total").value(1))
                .andExpect(jsonPath("$.data.today.assignees[0].chores[0].templateId").value(templateId))
                .andExpect(jsonPath("$.data.today.assignees[0].chores[0].completed").value(false));

        mockMvc.perform(put("/api/chores/templates/{id}/current-period-completion", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope": "TODAY", "periodStartDate": "2026-05-19"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.completed").value(true));

        mockMvc.perform(get("/api/chores/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today.summary.completed").value(1))
                .andExpect(jsonPath("$.data.today.assignees[0].chores[0].completed").value(true));

        mockMvc.perform(delete("/api/chores/templates/{id}/current-period-completion", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope": "TODAY", "periodStartDate": "2026-05-19"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.completed").value(false));

        mockMvc.perform(patch("/api/chores/templates/{id}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"archived": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true));

        mockMvc.perform(get("/api/chores/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today.summary.total").value(0));
    }

    @Test
    @WithMockFamily
    void activeFrom_afterCurrentPeriod_isExcludedFromBoard() throws Exception {
        mockMvc.perform(post("/api/chores/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Deep clean fridge",
                                  "assignedToMemberId": "00000000-0000-0000-0000-000000000002",
                                  "cadence": "MONTHLY",
                                  "activeFrom": "2026-06-01"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/chores/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thisMonth.summary.total").value(0));
    }

    @Test
    @WithMockFamily
    void completionAndUncompletion_stalePeriod_returns400() throws Exception {
        String templateId = createDailyTemplate();

        mockMvc.perform(put("/api/chores/templates/{id}/current-period-completion", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope": "TODAY", "periodStartDate": "2026-05-18"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chore period is stale. Refresh and try again."));

        mockMvc.perform(delete("/api/chores/templates/{id}/current-period-completion", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope": "TODAY", "periodStartDate": "2026-05-18"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chore period is stale. Refresh and try again."));
    }

    @Test
    @WithMockFamily
    void createTemplate_assigneeFromDifferentFamily_returns403ThroughRealStack() throws Exception {
        insertOtherFamilyAndMember();

        mockMvc.perform(post("/api/chores/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Should be rejected",
                                  "assignedToMemberId": "00000000-0000-0000-0000-000000000005",
                                  "cadence": "DAILY",
                                  "activeFrom": "2026-05-19"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        assertThat(choreTemplateCount()).isZero();
    }

    @Test
    @WithMockFamily
    void deleteFamilyMember_withActiveRecurringTemplate_returns400() throws Exception {
        createDailyTemplate();

        mockMvc.perform(delete("/api/family/members/{id}", MEMBER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Reassign or archive this member's recurring chores before deleting them."));
    }

    private String createDailyTemplate() throws Exception {
        String location = mockMvc.perform(post("/api/chores/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Brush teeth",
                                  "assignedToMemberId": "00000000-0000-0000-0000-000000000002",
                                  "cadence": "DAILY",
                                  "activeFrom": "2026-05-19"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void insertOtherFamilyAndMember() {
        jdbcTemplate.update(
                "INSERT INTO family (id, name, username, password_hash, timezone) VALUES (?, ?, ?, ?, ?)",
                OTHER_FAMILY_ID,
                "Other Family",
                "otherfamily",
                "$2a$10$dummyhashfortesting",
                "America/Los_Angeles"
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

    private int choreTemplateCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chore_template", Integer.class);
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-05-19T16:00:00Z"), ZoneOffset.UTC);
        }
    }
}
