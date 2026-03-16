package com.familyhub.demo.controller;

import com.familyhub.demo.config.SecurityConfig;
import com.familyhub.demo.dto.GoogleCalendarInfo;
import com.familyhub.demo.model.GoogleOAuthToken;
import com.familyhub.demo.model.GoogleSyncedCalendar;
import com.familyhub.demo.repository.GoogleOAuthTokenRepository;
import com.familyhub.demo.repository.GoogleSyncedCalendarRepository;
import com.familyhub.demo.security.JwtAuthenticationEntryPoint;
import com.familyhub.demo.security.JwtAuthenticationFilter;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.FamilyMemberService;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.GoogleCalendarListService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.familyhub.demo.TestDataFactory.MEMBER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GoogleCalendarController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@ActiveProfiles("test")
class GoogleCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FamilyService familyService;

    @MockitoBean
    private FamilyMemberService familyMemberService;

    @MockitoBean
    private GoogleCalendarListService calendarListService;

    @MockitoBean
    private GoogleSyncedCalendarRepository syncedCalendarRepository;

    @MockitoBean
    private GoogleOAuthTokenRepository tokenRepository;

    @Test
    @WithMockFamily
    void getCalendars_returnsMergedList() throws Exception {
        GoogleOAuthToken token = new GoogleOAuthToken();
        when(tokenRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(token));

        when(calendarListService.listCalendars(MEMBER_ID)).thenReturn(List.of(
                new GoogleCalendarInfo("primary", "Joe's Calendar", true),
                new GoogleCalendarInfo("work@group", "Work", false)
        ));

        GoogleSyncedCalendar synced = new GoogleSyncedCalendar();
        synced.setGoogleCalendarId("primary");
        synced.setEnabled(true);
        when(syncedCalendarRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(synced));

        mockMvc.perform(get("/api/google/calendars/{memberId}", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("primary"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[1].id").value("work@group"))
                .andExpect(jsonPath("$.data[1].enabled").value(false));
    }

    @Test
    @WithMockFamily
    void getCalendars_notConnected_returns400() throws Exception {
        when(tokenRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/google/calendars/{memberId}", MEMBER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockFamily
    void getCalendars_wrongFamily_returns403() throws Exception {
        doThrow(new AccessDeniedException("Unauthorized"))
                .when(familyMemberService).findById(any(), eq(MEMBER_ID));

        mockMvc.perform(get("/api/google/calendars/{memberId}", MEMBER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockFamily
    void putCalendars_updatesSelection() throws Exception {
        GoogleOAuthToken token = new GoogleOAuthToken();
        when(tokenRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(token));

        when(calendarListService.listCalendars(MEMBER_ID)).thenReturn(List.of(
                new GoogleCalendarInfo("primary", "Joe's Calendar", true),
                new GoogleCalendarInfo("work@group", "Work", false)
        ));

        when(syncedCalendarRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());

        mockMvc.perform(put("/api/google/calendars/{memberId}", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "calendarIds": ["primary"] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("primary"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[1].id").value("work@group"))
                .andExpect(jsonPath("$.data[1].enabled").value(false));

        verify(syncedCalendarRepository).save(any(GoogleSyncedCalendar.class));
    }

    @Test
    @WithMockFamily
    void putCalendars_notConnected_returns400() throws Exception {
        when(tokenRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/google/calendars/{memberId}", MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "calendarIds": ["primary"] }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCalendars_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/google/calendars/{memberId}", MEMBER_ID))
                .andExpect(status().isUnauthorized());
    }
}
