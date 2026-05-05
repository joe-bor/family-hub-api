package com.familyhub.demo.controller;

import com.familyhub.demo.config.SecurityConfig;
import com.familyhub.demo.dto.ChoreResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.security.JwtAuthenticationEntryPoint;
import com.familyhub.demo.security.JwtAuthenticationFilter;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.ChoreService;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.familyhub.demo.TestDataFactory.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@ActiveProfiles("test")
class ChoreControllerTest {

    private static final String CREATE_CHORE_JSON = """
            {
                "title": "🗑️ Take out trash",
                "assignedToMemberId": "00000000-0000-0000-0000-000000000002",
                "dueDate": "2026-05-05"
            }
            """;

    private static final String CREATE_CHORE_WITHOUT_DUE_DATE_JSON = """
            {
                "title": "🪥 Brush teeth",
                "assignedToMemberId": "00000000-0000-0000-0000-000000000002"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    FamilyService familyService;

    @MockitoBean
    ChoreService choreService;

    private ChoreResponse sampleCompletedChoreResponse() {
        return new ChoreResponse(
                CHORE_ID,
                "🗑️ Take out trash",
                MEMBER_ID,
                java.time.LocalDate.of(2026, 5, 5),
                true,
                LocalDateTime.of(2026, 5, 5, 10, 0),
                LocalDateTime.of(2026, 5, 5, 9, 0),
                LocalDateTime.of(2026, 5, 5, 10, 0)
        );
    }

    private ChoreResponse sampleUndatedChoreResponse() {
        return new ChoreResponse(
                CHORE_ID,
                "🪥 Brush teeth",
                MEMBER_ID,
                null,
                false,
                null,
                LocalDateTime.of(2026, 5, 5, 9, 0),
                LocalDateTime.of(2026, 5, 5, 9, 0)
        );
    }

    @Test
    @WithMockFamily
    void getChores_returns200() throws Exception {
        given(choreService.getChores(any(Family.class)))
                .willReturn(List.of(sampleChoreResponse()));

        mockMvc.perform(get("/api/chores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("🗑️ Take out trash"))
                .andExpect(jsonPath("$.data[0].assignedToMemberId")
                        .value("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    @WithMockFamily
    void createChore_returns201WithLocationHeader() throws Exception {
        given(choreService.createChore(any(), any(Family.class)))
                .willReturn(sampleChoreResponse());

        mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CHORE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/chores/" + CHORE_ID))
                .andExpect(jsonPath("$.message").value("Chore created successfully"));
    }

    @Test
    @WithMockFamily
    void createChore_withoutDueDate_returns201() throws Exception {
        given(choreService.createChore(any(), any(Family.class)))
                .willReturn(sampleUndatedChoreResponse());

        mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CHORE_WITHOUT_DUE_DATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dueDate").isEmpty());
    }

    @Test
    @WithMockFamily
    void createChore_assigneeFromDifferentFamily_returns403() throws Exception {
        given(choreService.createChore(any(), any(Family.class)))
                .willThrow(new AccessDeniedException("Unauthorized"));

        mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_CHORE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @WithMockFamily
    void createChore_emptyTitle_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "",
                                    "assignedToMemberId": "00000000-0000-0000-0000-000000000002"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    @Test
    @WithMockFamily
    void createChore_nullAssignedToMemberId_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/chores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "🗑️ Take out trash"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'assignedToMemberId')]").exists());
    }

    @Test
    @WithMockFamily
    void updateChore_toggleComplete_returns200() throws Exception {
        given(choreService.updateChore(eq(CHORE_ID), any(), any(Family.class)))
                .willReturn(sampleCompletedChoreResponse());

        mockMvc.perform(patch("/api/chores/{id}", CHORE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "completed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andExpect(jsonPath("$.message").value("Chore updated successfully"));
    }

    @Test
    @WithMockFamily
    void updateChore_emptyBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/chores/{id}", CHORE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockFamily
    void deleteChore_returns204() throws Exception {
        willDoNothing().given(choreService).deleteChore(eq(CHORE_ID), any(Family.class));

        mockMvc.perform(delete("/api/chores/{id}", CHORE_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void getChores_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/chores"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.httpStatus").value(401));
    }
}
