package com.familyhub.demo.controller;

import com.familyhub.demo.TestDataFactory;
import com.familyhub.demo.config.SecurityConfig;
import com.familyhub.demo.dto.CalendarEventResponse;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.security.JwtAuthenticationEntryPoint;
import com.familyhub.demo.security.JwtAuthenticationFilter;
import com.familyhub.demo.security.WithMockFamily;
import com.familyhub.demo.service.CalendarEventService;
import com.familyhub.demo.service.FamilyService;
import com.familyhub.demo.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarEventController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@ActiveProfiles("test")
class CalendarEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarEventService calendarEventService;

    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private FamilyService familyService;

    @Test
    @WithMockFamily
    void getEvents_returns200() throws Exception {
        CalendarEventResponse eventResponse = TestDataFactory.createCalendarEventResponse();
        when(calendarEventService.getAllEventsByFamily(any(Family.class), any(), any(), any()))
                .thenReturn(List.of(eventResponse));

        mockMvc.perform(get("/api/calendar/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Test Event"))
                .andExpect(jsonPath("$.data[0].startTime").value("9:00 AM"));
    }

    @Test
    @WithMockFamily
    void getEvents_withFilters_returns200() throws Exception {
        when(calendarEventService.getAllEventsByFamily(any(Family.class), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/calendar/events")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-03-31")
                        .param("memberId", TestDataFactory.MEMBER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockFamily
    void getEventById_returns200() throws Exception {
        CalendarEventResponse eventResponse = TestDataFactory.createCalendarEventResponse();
        when(calendarEventService.getEventById(eq(TestDataFactory.EVENT_ID), any(Family.class)))
                .thenReturn(eventResponse);

        mockMvc.perform(get("/api/calendar/events/{id}", TestDataFactory.EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Test Event"));
    }

    @Test
    @WithMockFamily
    void addEvent_returns201WithLocation() throws Exception {
        CalendarEventResponse eventResponse = TestDataFactory.createCalendarEventResponse();
        when(calendarEventService.addCalendarEvent(any(), any(Family.class)))
                .thenReturn(eventResponse);

        mockMvc.perform(post("/api/calendar/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Test Event",
                                    "startTime": "9:00 AM",
                                    "endTime": "10:00 AM",
                                    "date": "2026-03-15",
                                    "memberId": "%s"
                                }
                                """.formatted(TestDataFactory.MEMBER_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.message").value("Event created successfully"));
    }

    @Test
    @WithMockFamily
    void addEvent_validationError_returns400() throws Exception {
        mockMvc.perform(post("/api/calendar/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "",
                                    "startTime": "invalid",
                                    "endTime": "",
                                    "date": null,
                                    "memberId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @WithMockFamily
    void updateEvent_returns200() throws Exception {
        CalendarEventResponse eventResponse = TestDataFactory.createCalendarEventResponse();
        when(calendarEventService.updateCalendarEvent(any(), eq(TestDataFactory.EVENT_ID), any(Family.class)))
                .thenReturn(eventResponse);

        mockMvc.perform(put("/api/calendar/events/{id}", TestDataFactory.EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Event",
                                    "startTime": "9:00 AM",
                                    "endTime": "10:00 AM",
                                    "date": "2026-03-15",
                                    "memberId": "%s"
                                }
                                """.formatted(TestDataFactory.MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event updated successfully"));
    }

    @Test
    @WithMockFamily
    void deleteEvent_returns204() throws Exception {
        mockMvc.perform(delete("/api/calendar/events/{id}", TestDataFactory.EVENT_ID))
                .andExpect(status().isNoContent());

        verify(calendarEventService).deleteCalendarEvent(eq(TestDataFactory.EVENT_ID), any(Family.class));
    }

    @Test
    void getEvents_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/calendar/events"))
                .andExpect(status().isUnauthorized());
    }
}
